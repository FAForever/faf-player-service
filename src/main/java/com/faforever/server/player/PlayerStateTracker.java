package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.geoip.GeoIpService;
import com.faforever.server.interceptor.cdi.MDCInterceptor;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.micrometer.PlayerSessionStateResolver;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.rating.LeaderboardRatingRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
class PlayerStateTracker {

    private final GeoIpService geoIpService;

    private final Emitter<Map<String, String>> playerConnectionsRequestEmitter;
    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;

    private final PlayerRepository playerRepository;
    private final AvatarRepository avatarRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;
    private final LeaderboardRatingRepository leaderboardRatingRepository;

    private final Map<Integer, Player> playerIdMap;
    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> dirtyPlayers;

    private final Map<Long, Player> sessionPlayerMap = new ConcurrentHashMap<>();
    private final Map<Long, Instant> sessionExpirationMap = new ConcurrentHashMap<>();
    private final Map<Player, Set<Long>> playerSessionMap = new ConcurrentHashMap<>();

    private Instant lastRatingUpdateTime = Instant.now();

    PlayerStateTracker(GeoIpService geoIpService,
                       @Channel("player-session-request") Emitter<Map<String, String>> playerConnectionsRequestEmitter,
                       @Channel(
                               "lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
                       PlayerRepository playerRepository, AvatarRepository avatarRepository,
                       FriendOrFoeRepository friendOrFoeRepository,
                       UserGroupAssignmentRepository userGroupAssignmentRepository,
                       LeaderboardRatingRepository leaderboardRatingRepository, MeterRegistry meterRegistry) {
        this.geoIpService = geoIpService;
        this.playerConnectionsRequestEmitter = playerConnectionsRequestEmitter;
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.playerRepository = playerRepository;
        this.avatarRepository = avatarRepository;
        this.friendOrFoeRepository = friendOrFoeRepository;
        this.userGroupAssignmentRepository = userGroupAssignmentRepository;
        this.leaderboardRatingRepository = leaderboardRatingRepository;
        this.playerIdMap = meterRegistry.gaugeMapSize("faf.players", Tags.of("state", "online"),
                                                      new ConcurrentHashMap<>());
        this.dirtyPlayers = meterRegistry.gaugeCollectionSize("faf.players", Tags.of("state", "dirty"),
                                                              ConcurrentHashMap.newKeySet());
    }

    @PostConstruct
    void registerExistingPlayers() {
        playerConnectionsRequestEmitter.send(Map.of());
    }

    @Incoming("player-session-state")
    @Timed(value = "faf.player.session.update", histogram = true)
    @MDCPopulated
    void handlePlayerSessionStatus(@MeterTag(key = "state",
                                             resolver = PlayerSessionStateResolver.class) PlayerSessionState playerSessionStatus) {
        switch (playerSessionStatus.state()) {
            case PlayerSessionState.State.New(Duration secondsToExpiry) ->
                    registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                          playerSessionStatus.ipAddress(), true, secondsToExpiry);

            case PlayerSessionState.State.Existing(Duration secondsToExpiry) ->
                    registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                          playerSessionStatus.ipAddress(), false, secondsToExpiry);
            case PlayerSessionState.State.Gone() ->
                    unregisterPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId());
        }
    }

    private void registerPlayerSession(long sessionId, int playerId, String ipAddress, boolean newConnection,
                                       Duration secondsToExpiry) {
        LOG.atDebug()
           .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
           .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
           .log("Registering session");
        Player player = playerIdMap.computeIfAbsent(playerId, key -> {
            Player loadedPlayer = playerRepository.loadPlayer(key);
            loadedPlayer.setCountry(geoIpService.findCountry(ipAddress));
            return loadedPlayer;
        });
        Player existingPlayer = sessionPlayerMap.putIfAbsent(sessionId, player);
        if (existingPlayer != null && existingPlayer != player) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(existingPlayer.getId()));
        }
        playerSessionMap.compute(player, (_, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(sessionId);
            return sessions;
        });

        sessionExpirationMap.put(sessionId, Instant.now().plus(secondsToExpiry));

        if (!newConnection) {
            return;
        }

        LOG.atInfo()
           .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
           .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
           .log("Registered new session");
        playerRepository.updatePlayerIpAddress(playerId, ipAddress);
        sendWelcomeMessages(sessionId, player);
        dirtyPlayers.add(player);
    }

    private void sendWelcomeMessages(long sessionId, Player player) {
        Set<String> channels = new HashSet<>();
        if (player.getClan() != null) {
            channels.add("#%s_clan".formatted(player.getClan()));
        }
        if (userGroupAssignmentRepository.isPlayerModerator(player.getId())) {
            channels.add("#moderators");
        }

        Map<FriendOrFoeEntity.Status, Set<Integer>> friendsAndFoes = friendOrFoeRepository.loadFriendsAndFoes(
                player.getId());

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                                                                         new ConnectionMessage.LoginSuccessResponse(
                                                                                 PlayerInfo.from(player),
                                                                                 OffsetDateTime.now())));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                                                                         new SocialMessage.SocialInfo(channels,
                                                                                                      friendsAndFoes.getOrDefault(
                                                                                                              FriendOrFoeEntity.Status.FRIEND,
                                                                                                              Set.of()),
                                                                                                      friendsAndFoes.getOrDefault(
                                                                                                              FriendOrFoeEntity.Status.FOE,
                                                                                                              Set.of()))));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId, new SocialMessage.PlayerInfoList(
                playerIdMap.values().stream().map(PlayerInfo::from).collect(Collectors.toSet()))));
    }

    private void unregisterPlayerSession(long sessionId, int playerId) {
        LOG.atInfo()
           .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
           .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
           .log("Removing session");
        playerIdMap.computeIfPresent(playerId, (_, player) -> {
            if (!sessionPlayerMap.remove(sessionId, player)) {
                return player;
            }

            Set<Long> remainingSessions = playerSessionMap.computeIfPresent(player, (_, sessions) -> {
                if (sessions.remove(sessionId) && sessions.isEmpty()) {
                    return null;
                } else {
                    return sessions;
                }
            });

            if (remainingSessions == null) {
                player.markOffline();
                dirtyPlayers.add(player);
                return null;
            } else {
                return player;
            }
        });
    }

    @Incoming("avatar-updates")
    @Timed(value = "faf.player.avatar", histogram = true)
    void handlePlayerAvatar(PlayerAvatarUpdate playerAvatarUpdate) {
        LOG.info("Updating avatar for player id {} to avatar id {}", playerAvatarUpdate.playerId(),
                 playerAvatarUpdate.avatarId());
        updatePlayer(playerAvatarUpdate.playerId(), player -> {
            if (playerAvatarUpdate.avatarId() == null) {
                player.clearAvatar();
            } else {
                player.setAvatar(avatarRepository.findAvatar(playerAvatarUpdate.avatarId()));
            }
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.session.remove", histogram = true)
    void removeExpiredSessions() {
        Instant evaluationTime = Instant.now();

        Set<Long> expiredSessionIds = sessionExpirationMap.entrySet()
                                                          .stream()
                                                          .filter(entry -> evaluationTime.isAfter(entry.getValue()))
                                                          .map(Map.Entry::getKey)
                                                          .collect(Collectors.toSet());
        if (expiredSessionIds.isEmpty()) {
            LOG.debug("No expired sessions found");
            return;
        }

        LOG.info("Removing {} expired sessions", expiredSessionIds.size());
        expiredSessionIds.forEach(expiredSessionId -> {
            Player player = sessionPlayerMap.get(expiredSessionId);
            if (player != null) {
                unregisterPlayerSession(expiredSessionId, player.getId());
            }
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.leaderboard.update", histogram = true)
    void updatePlayerLeaderboardRatings() {
        Instant currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = Instant.now();
        Set<Integer> playerIds = Set.copyOf(playerIdMap.keySet());
        if (playerIds.isEmpty()) {
            LOG.debug("No updated ratings found");
            return;
        }

        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = leaderboardRatingRepository.loadUpdatedLeaderboardRatings(
                playerIds, currentRatingUpdateTime);
        LOG.info("Updating ratings for {} players", playerIdRatingMap.size());

        playerIdRatingMap.forEach((playerId, newLeaderboardRatings) -> updatePlayer(playerId,
                                                                                    player -> player.updateLeaderboardRatings(
                                                                                            newLeaderboardRatings)));
    }

    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.dirty.update", histogram = true)
    void sendUpdateForDirtyPlayers() {
        Set<Player> frozenDirtyPlayers = Set.copyOf(dirtyPlayers);
        if (frozenDirtyPlayers.isEmpty()) {
            return;
        }
        LOG.debug("Sending player info for {} players", frozenDirtyPlayers.size());

        Set<PlayerInfo> playerInfos = frozenDirtyPlayers.stream().map(PlayerInfo::from).collect(Collectors.toSet());
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(playerInfos)));

        dirtyPlayers.removeAll(frozenDirtyPlayers);
    }

    void updatePlayer(int playerId, Consumer<Player> updater) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }
        updater.accept(player);
        dirtyPlayers.add(player);
    }
}
