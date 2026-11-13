package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.geoip.GeoIpService;
import com.faforever.server.interceptor.cdi.MDCInterceptor;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.LeaderboardStats;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.micrometer.PlayerSessionStateResolver;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
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

    private final Clock clock;

    private final PlayerStateRepository playerStateRepository;

    private final String defaultChannelName;

    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, Player> playerIdMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, PlayerInfo> dirtyPlayerInfoIdMap;

    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, Integer> sessionPlayerIdMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, Instant> sessionExpirationMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, Set<Long>> playerIdSessionMap = new ConcurrentHashMap<>();

    private Instant lastRatingUpdateTime = Instant.now();

    PlayerStateTracker(GeoIpService geoIpService,
                       @Channel("player-session-request") Emitter<Map<String, String>> playerConnectionsRequestEmitter,
                       @Channel(
                               "lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
                       Clock clock, PlayerStateRepository playerStateRepository,
                       @ConfigProperty(name = "faf.default-channel-name",
                                       defaultValue = "#aeolus") String defaultChannelName,
                       MeterRegistry meterRegistry) {
        this.geoIpService = geoIpService;
        this.playerConnectionsRequestEmitter = playerConnectionsRequestEmitter;
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.clock = clock;
        this.playerStateRepository = playerStateRepository;
        this.defaultChannelName = defaultChannelName;
        this.playerIdMap = meterRegistry.gaugeMapSize("faf.players", Tags.of("state", "online"),
                                                      new ConcurrentHashMap<>());
        this.dirtyPlayerInfoIdMap = meterRegistry.gaugeMapSize("faf.players", Tags.of("state", "dirty"),
                                                               new ConcurrentHashMap<>());
        this.sessionPlayerIdMap = meterRegistry.gaugeMapSize("faf.sessions", Tags.of("state", "online"),
                                                             new ConcurrentHashMap<>());
        this.sessionExpirationMap = Objects.requireNonNull(
                meterRegistry.gauge("faf.sessions", Tags.of("state", "expired"), new ConcurrentHashMap<>(),
                                    sessionsExpiredMap -> sessionsExpiredMap.values()
                                                                            .stream()
                                                                            .filter(instant -> instant.isAfter(
                                                                                    clock.instant()))
                                                                            .count()));
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
            case PlayerSessionState.State.New(Duration secondsToExpiry) -> {
                Player player = registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                                      playerSessionStatus.ipAddress(), secondsToExpiry);

                sendWelcomeMessages(playerSessionStatus.sessionId(), player);
                LOG.info("Registered new session");
            }

            case PlayerSessionState.State.Existing(Duration secondsToExpiry) ->
                    registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                          playerSessionStatus.ipAddress(), secondsToExpiry);
            case PlayerSessionState.State.Gone() -> unregisterSession(playerSessionStatus.sessionId());
        }
    }

    private Player registerPlayerSession(long sessionId, int playerId, String ipAddress, Duration secondsToExpiry) {
        LOG.debug("Registering session");
        Integer existingPlayerId = sessionPlayerIdMap.putIfAbsent(sessionId, playerId);
        if (existingPlayerId != null && existingPlayerId != playerId) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(existingPlayerId));
        }
        sessionExpirationMap.put(sessionId, clock.instant().plus(secondsToExpiry));

        return playerIdMap.compute(playerId, (key, player) -> {
            if (player == null) {
                player = initializePlayer(key, ipAddress);
            }

            playerIdSessionMap.compute(playerId, (_, sessions) -> {
                if (sessions == null) {
                    sessions = ConcurrentHashMap.newKeySet();
                }
                sessions.add(sessionId);
                return sessions;
            });

            return player;
        });
    }

    private Player initializePlayer(int playerId, String ipAddress) {
        Player player = playerStateRepository.loadPlayer(playerId)
                                             .orElseThrow(() -> new IllegalStateException(
                                                     "No user with id %d".formatted(playerId)));

        playerStateRepository.updatePlayerIpAddress(playerId, ipAddress);

        Collection<LeaderboardRating> leaderboardRatings = playerStateRepository.loadPlayerRatings(playerId);

        player.updateLeaderboardRatings(leaderboardRatings);
        player.setCountry(geoIpService.findCountry(ipAddress));
        return player;
    }

    private void sendWelcomeMessages(long sessionId, Player player) {
        Set<String> channels = new HashSet<>();
        channels.add(defaultChannelName);
        if (player.getClan() != null) {
            channels.add("#%s_clan".formatted(player.getClan()));
        }
        if (playerStateRepository.isPlayerModerator(player.getId())) {
            channels.add("#moderators");
        }

        Map<FriendOrFoeEntity.Status, Set<Integer>> friendsAndFoes = playerStateRepository.loadFriendsAndFoes(
                                                                                                  player.getId())
                                                                                          .stream()
                                                                                          .collect(
                                                                                                  Collectors.groupingBy(
                                                                                                          FriendOrFoe::status,
                                                                                                          Collectors.mapping(
                                                                                                                  FriendOrFoe::subjectId,
                                                                                                                  Collectors.toSet())));

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                                                                         new ConnectionMessage.LoginSuccessResponse(
                                                                                 toPlayerInfo(player),
                                                                                 clock.instant())));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                                                                         new SocialMessage.SocialInfo(channels,
                                                                                                      friendsAndFoes.getOrDefault(
                                                                                                              FriendOrFoeEntity.Status.FRIEND,
                                                                                                              Set.of()),
                                                                                                      friendsAndFoes.getOrDefault(
                                                                                                              FriendOrFoeEntity.Status.FOE,
                                                                                                              Set.of()))));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId, new SocialMessage.PlayerInfoList(
                playerIdMap.values().stream().map(this::toPlayerInfo).collect(Collectors.toSet()))));

        markDirty(player);
    }

    private void unregisterSession(long sessionId) {
        LOG.info("Removing session");
        sessionExpirationMap.remove(sessionId);
        Integer playerId = sessionPlayerIdMap.remove(sessionId);
        if (playerId == null) {
            LOG.warn("No player associated with session");
            return;
        }

        playerIdMap.computeIfPresent(playerId, (key, player) -> {
            Set<Long> remainingSessions = playerIdSessionMap.computeIfPresent(key, (_, sessions) -> {
                if (sessions.remove(sessionId) && sessions.isEmpty()) {
                    return null;
                } else {
                    return sessions;
                }
            });

            if (remainingSessions == null) {
                markDirty(player);
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
                player.setAvatar(playerStateRepository.findAvatar(playerAvatarUpdate.avatarId()).orElseThrow());
            }
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.session.remove", histogram = true)
    void removeExpiredSessions() {
        Instant evaluationTime = clock.instant();

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
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(expiredSessionId));
            Integer playerId = sessionPlayerIdMap.get(expiredSessionId);
            if (playerId != null) {
                MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(playerId));
            }

            unregisterSession(expiredSessionId);

            MDC.remove(MDCInterceptor.SESSION_ID);
            MDC.remove(MDCInterceptor.PLAYER_ID);
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.leaderboard.update", histogram = true)
    void updatePlayerLeaderboardRatings() {
        Instant currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = clock.instant();
        Set<Integer> playerIds = Set.copyOf(playerIdMap.keySet());
        if (playerIds.isEmpty()) {
            LOG.debug("No players connected");
            return;
        }

        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = playerStateRepository.loadUpdatedLeaderboardRatings(
                                                                                              playerIds, currentRatingUpdateTime)
                                                                                      .stream()
                                                                                      .collect(Collectors.groupingBy(
                                                                                              PlayerLeaderboardRating::playerId,
                                                                                              Collectors.mapping(
                                                                                                      PlayerLeaderboardRating::leaderboardRating,
                                                                                                      Collectors.toSet())));

        if (playerIdRatingMap.isEmpty()) {
            LOG.debug("No updated ratings found");
            return;
        }

        LOG.info("Updating ratings for {} players", playerIdRatingMap.size());

        playerIdRatingMap.forEach((playerId, newLeaderboardRatings) -> updatePlayer(playerId,
                                                                                    player -> player.updateLeaderboardRatings(
                                                                                            newLeaderboardRatings)));
    }

    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.dirty.update", histogram = true)
    void sendUpdateForDirtyPlayers() {
        Map<Integer, PlayerInfo> frozenDirtyPlayers = Map.copyOf(dirtyPlayerInfoIdMap);
        if (frozenDirtyPlayers.isEmpty()) {
            return;
        }
        LOG.debug("Sending player info for {} players", frozenDirtyPlayers.size());

        outboundLobbyMessageEmitter.send(
                OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(frozenDirtyPlayers.values())));

        frozenDirtyPlayers.forEach(dirtyPlayerInfoIdMap::remove);
    }

    void updatePlayer(int playerId, Consumer<Player> updater) {
        playerIdMap.computeIfPresent(playerId, (_, player) -> {
            updater.accept(player);
            markDirty(player);
            return player;
        });
    }

    private void markDirty(Player player) {
        dirtyPlayerInfoIdMap.put(player.getId(), toPlayerInfo(player));
    }

    private PlayerInfo toPlayerInfo(Player player) {
        Map<String, LeaderboardStats> leaderboardStatsMap = player.getLeaderboardRatings()
                                                                  .stream()
                                                                  .collect(Collectors.toMap(
                                                                          LeaderboardRating::leaderboard,
                                                                          leaderboardRating -> {
                                                                              LeaderboardStats.Rating rating = new LeaderboardStats.Rating(
                                                                                      leaderboardRating.mean(),
                                                                                      leaderboardRating.deviation());
                                                                              return new LeaderboardStats(
                                                                                      leaderboardRating.totalGames(),
                                                                                      rating);
                                                                          }));
        PlayerInfo.State state = playerIdSessionMap.containsKey(player.getId()) ? null : PlayerInfo.State.OFFLINE;
        String clan = player.getClan();
        Avatar playerAvatar = player.getAvatar();
        AvatarInfo avatar = playerAvatar == null ?
                            null :
                            new AvatarInfo(playerAvatar.url(), playerAvatar.description());
        return new PlayerInfo(player.getId(), player.getUsername(), clan, avatar, player.getCountry(),
                              leaderboardStatsMap, state);
    }

}
