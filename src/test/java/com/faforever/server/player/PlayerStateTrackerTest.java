package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.domain.ClanEntity;
import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain.LeaderboardEntity;
import com.faforever.server.domain.LeaderboardRatingEntity;
import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.geoip.GeoIpService;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.LeaderboardStats;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.OutboundTarget;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.rating.LeaderboardRatingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class PlayerStateTrackerTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;
    private static final PlayerInfo PLAYER_INFO = new PlayerInfo(PLAYER_ID, "test", null,
                                                                 new AvatarInfo("temp", "temporary"), "",
                                                                 Map.of("leaderboard", new LeaderboardStats(1,
                                                                                                            new LeaderboardStats.Rating(
                                                                                                                    1000,
                                                                                                                    100))),
                                                                 null);

    @Inject
    PlayerStateTracker playerStateTracker;

    @InjectMock
    private FriendOrFoeRepository friendOrFoeRepository;
    @InjectMock
    private GeoIpService geoIpService;
    @InjectMock
    private AvatarRepository avatarRepository;
    @InjectMock
    private LeaderboardRatingRepository leaderboardRatingRepository;
    @InjectMock
    private Emitter<Map<String, String>> playerConnectionsRequestEmitter;
    @InjectMock
    private Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;
    @InjectMock
    private PlayerRepository playerRepository;
    @InjectMock
    private UserGroupAssignmentRepository userGroupAssignmentRepository;

    private final PlayerEntity playerEntity = new PlayerEntity();

    @BeforeEach
    void setup() {
        playerEntity.setId(PLAYER_ID);

        AvatarEntity avatarEntity = new AvatarEntity();
        avatarEntity.setId(10);
        avatarEntity.setDescription("temporary");
        avatarEntity.setUrl("temp");
        playerEntity.setSelectedAvatar(avatarEntity);

        playerEntity.setName("test");

        LeaderboardRatingEntity leaderboardRatingEntity = new LeaderboardRatingEntity();

        LeaderboardEntity leaderboardEntity = new LeaderboardEntity();
        leaderboardEntity.setTechnicalName("leaderboard");
        leaderboardRatingEntity.setLeaderboard(leaderboardEntity);

        leaderboardRatingEntity.setTotalGames(1);
        leaderboardRatingEntity.setMean(1000d);
        leaderboardRatingEntity.setDeviation(100d);

        when(leaderboardRatingRepository.loadPlayerRatings(PLAYER_ID)).thenReturn(List.of(leaderboardRatingEntity));
    }

    @Test
    void testExistingPlayersRegistered() {
        assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));

        verify(playerConnectionsRequestEmitter).send(Map.of());
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            when(friendOrFoeRepository.loadFriendsAndFoes(PLAYER_ID)).thenReturn(
                    List.of(new FriendOrFoeEntity(new FriendOrFoeEntity.Id(PLAYER_ID, 3),
                                                  FriendOrFoeEntity.Status.FRIEND),
                            new FriendOrFoeEntity(new FriendOrFoeEntity.Id(PLAYER_ID, 4),
                                                  FriendOrFoeEntity.Status.FOE)));
            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
            when(geoIpService.findCountry("127.0.0.1")).thenReturn("");

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1))));

            verify(playerRepository).updatePlayerIpAddress(PLAYER_ID, "127.0.0.1");

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(Player.State.ONLINE, dirtyPlayer.getState());
            assertEquals(PLAYER_ID, dirtyPlayer.getId());
            assertEquals(Set.of(new LeaderboardRating("leaderboard", 1, 1000, 100)),
                         dirtyPlayer.getLeaderboardRatings());

            ArgumentCaptor<OutboundLobbyMessage<LobbyMessage.Server>> messageRequestCaptor = captor();
            verify(outboundLobbyMessageEmitter, times(3)).send(messageRequestCaptor.capture());

            List<OutboundLobbyMessage<LobbyMessage.Server>> sentMessages = messageRequestCaptor.getAllValues();

            OutboundLobbyMessage<?> firstMessage = sentMessages.getFirst();
            OutboundTarget firstTarget = firstMessage.target();
            assertThat(firstTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session firstSessionTarget = (OutboundTarget.Session) firstTarget;
            assertThat(firstSessionTarget.sessionId(), equalTo(SESSION_ID));

            assertThat(firstMessage.message(), isA(ConnectionMessage.LoginSuccessResponse.class));

            ConnectionMessage.LoginSuccessResponse loginSuccessResponse = (ConnectionMessage.LoginSuccessResponse) firstMessage.message();
            assertThat(loginSuccessResponse.currentTime(), lessThanOrEqualTo(OffsetDateTime.now()));
            assertThat(loginSuccessResponse.me(), equalTo(PLAYER_INFO));

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1);
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                       equalTo(new SocialMessage.SocialInfo(Set.of(), Set.of(3), Set.of(4))));

            OutboundLobbyMessage<?> thirdMessage = sentMessages.getLast();
            OutboundTarget thirdTarget = thirdMessage.target();
            assertThat(thirdTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session thirdSessionTarget = (OutboundTarget.Session) thirdTarget;

            assertThat(thirdSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(thirdMessage.message(), isA(SocialMessage.PlayerInfoList.class));

            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getSessionPlayerMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerSessionMap().keySet(), hasSize(1));
        }

        @Test
        void testRegisterSessionAsModerator() {
            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
            when(geoIpService.findCountry("127.0.0.1")).thenReturn("");

            when(userGroupAssignmentRepository.isPlayerModerator(PLAYER_ID)).thenReturn(true);

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1))));

            ArgumentCaptor<OutboundLobbyMessage<LobbyMessage.Server>> messageRequestCaptor = captor();
            verify(outboundLobbyMessageEmitter, times(3)).send(messageRequestCaptor.capture());

            List<OutboundLobbyMessage<LobbyMessage.Server>> sentMessages = messageRequestCaptor.getAllValues();

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1);
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                       equalTo(new SocialMessage.SocialInfo(Set.of("#moderators"), Set.of(), Set.of())));
        }

        @Test
        void testRegisterSessionClan() {
            ClanEntity clan = new ClanEntity();
            clan.setTag("TEST");
            playerEntity.setClan(clan);

            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
            when(geoIpService.findCountry("127.0.0.1")).thenReturn("");

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1))));

            ArgumentCaptor<OutboundLobbyMessage<LobbyMessage.Server>> messageRequestCaptor = captor();
            verify(outboundLobbyMessageEmitter, times(3)).send(messageRequestCaptor.capture());

            List<OutboundLobbyMessage<LobbyMessage.Server>> sentMessages = messageRequestCaptor.getAllValues();

            OutboundLobbyMessage<?> firstMessage = sentMessages.getFirst();
            OutboundTarget firstTarget = firstMessage.target();
            assertThat(firstTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session firstSessionTarget = (OutboundTarget.Session) firstTarget;
            assertThat(firstSessionTarget.sessionId(), equalTo(SESSION_ID));

            assertThat(firstMessage.message(), isA(ConnectionMessage.LoginSuccessResponse.class));

            ConnectionMessage.LoginSuccessResponse loginSuccessResponse = (ConnectionMessage.LoginSuccessResponse) firstMessage.message();
            assertThat(loginSuccessResponse.currentTime(), lessThanOrEqualTo(OffsetDateTime.now()));
            assertThat(loginSuccessResponse.me(),
                       equalTo(new PlayerInfo(PLAYER_INFO.id(), PLAYER_INFO.login(), "TEST", PLAYER_INFO.avatar(),
                                              PLAYER_INFO.country(), PLAYER_INFO.ratings(), PLAYER_INFO.state())));

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1);
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                       equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan"), Set.of(), Set.of())));
        }

        @Test
        void testRegisterSessionBadId() {
            assertThrows(IllegalStateException.class, () -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1)))));
        }

        @Test
        void testNoActionWhenNoDirty() {
            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
            playerStateTracker.sendUpdateForDirtyPlayers();
            verifyNoInteractions(outboundLobbyMessageEmitter);
        }

        @Test
        void testUnregisterSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Gone()));

            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUpdateLeaderboardsNoPlayers() {
            playerStateTracker.updatePlayerLeaderboardRatings();

            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUpdatePlayerAvatar() {
            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, 2));

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(0));

            verifyNoInteractions(avatarRepository);
        }

    }

    @Nested
    class Registered {

        @BeforeEach
        void setup() {
            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(playerEntity));
            when(geoIpService.findCountry("127.0.0.1")).thenReturn("");

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Existing(
                            Duration.ofMinutes(1))));
        }

        @Test
        void testDoubleRegistrationThrowsWhenDifferentPlayers() {
            when(playerRepository.loadPlayer(2)).thenReturn(Optional.of(playerEntity));
            assertThrows(IllegalStateException.class, () -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(2, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1)))));
            assertThrows(IllegalStateException.class, () -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(2, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Existing(
                            Duration.ofSeconds(1)))));
        }

        @Test
        void testDoubleRegistrationDoesNotThrowWhenSamePlayer() {
            assertDoesNotThrow(() -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1)))));
            assertDoesNotThrow(() -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Existing(
                            Duration.ofSeconds(1)))));
        }

        @Test
        void testUnregisterSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Gone()));

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(Player.State.OFFLINE, dirtyPlayer.getState());
            assertEquals(PLAYER_ID, dirtyPlayer.getId());
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionPlayerMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getPlayerSessionMap().keySet(), hasSize(0));
        }

        @Test
        void testUnregisterNonRegisteredSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, 2, "127.0.0.1", new PlayerSessionState.State.Gone()));

            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUnregisterSessionMultipleSessions() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, 2, "127.0.0.1", new PlayerSessionState.State.New(
                            Duration.ofSeconds(1))));

            Map<Integer, Player> dirtyPlayersAfterBeforeGone = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayersAfterBeforeGone.keySet(), hasSize(1));

            Player dirtyPlayerBeforeGone = dirtyPlayersAfterBeforeGone.get(PLAYER_ID);
            assertEquals(Player.State.ONLINE, dirtyPlayerBeforeGone.getState());
            assertEquals(PLAYER_ID, dirtyPlayerBeforeGone.getId());
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(2));
            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getSessionPlayerMap().keySet(), hasSize(2));
            assertThat(playerStateTracker.getPlayerSessionMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerSessionMap().get(dirtyPlayerBeforeGone), hasSize(2));

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Gone()));

            Map<Integer, Player> dirtyPlayersAfterGone = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayersAfterGone.keySet(), hasSize(1));

            Player dirtyPlayerAfterGone = dirtyPlayersAfterGone.get(PLAYER_ID);
            assertEquals(Player.State.ONLINE, dirtyPlayerAfterGone.getState());
            assertEquals(PLAYER_ID, dirtyPlayerAfterGone.getId());
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getSessionPlayerMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerSessionMap().keySet(), hasSize(1));
        }

        @Test
        void testUpdatePlayer() {
            playerStateTracker.updatePlayer(PLAYER_ID, _ -> {});
            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(PLAYER_ID, dirtyPlayer.getId());

            playerStateTracker.sendUpdateForDirtyPlayers();

            verify(outboundLobbyMessageEmitter).send(
                    OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(Set.of(PLAYER_INFO))));
            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUpdateLeaderboards() {

            LeaderboardEntity leaderboardEntity = new LeaderboardEntity();
            leaderboardEntity.setTechnicalName("leaderboard");

            LeaderboardRatingEntity leaderboardRatingEntity = new LeaderboardRatingEntity();
            leaderboardRatingEntity.setDeviation(0d);
            leaderboardRatingEntity.setMean(0d);
            leaderboardRatingEntity.setPlayerId(PLAYER_ID);
            leaderboardRatingEntity.setTotalGames(2);
            leaderboardRatingEntity.setLeaderboard(leaderboardEntity);
            when(leaderboardRatingRepository.loadUpdatedLeaderboardRatings(anyCollection(), any())).thenReturn(
                    List.of(leaderboardRatingEntity));

            LeaderboardRating leaderboardRating = LeaderboardRating.from(leaderboardRatingEntity);

            playerStateTracker.updatePlayerLeaderboardRatings();

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(Set.of(leaderboardRating), dirtyPlayer.getLeaderboardRatings());
            assertEquals(PLAYER_ID, dirtyPlayer.getId());
        }

        @Test
        void testUpdateLeaderboardsNoUpdates() {
            playerStateTracker.updatePlayerLeaderboardRatings();

            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
        }

        @Test
        void testClearPlayerAvatar() {
            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, null));

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(PLAYER_ID, dirtyPlayer.getId());
            assertNull(dirtyPlayer.getAvatar());
        }

        @Test
        void testUpdatePlayerAvatar() {
            int avatarId = 2;
            AvatarEntity avatar = new AvatarEntity();
            avatar.setId(avatarId);
            avatar.setUrl("temp");
            avatar.setDescription("other");
            when(avatarRepository.findAvatar(avatarId)).thenReturn(Optional.of(avatar));

            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, avatarId));

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(PLAYER_ID, dirtyPlayer.getId());
            assertEquals(Avatar.from(avatar), dirtyPlayer.getAvatar());
        }

        @Test
        void testExpiredSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Existing(
                            Duration.ofNanos(-1))));

            playerStateTracker.removeExpiredSessions();

            Map<Integer, Player> dirtyPlayers = playerStateTracker.getDirtyPlayersIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(1));

            Player dirtyPlayer = dirtyPlayers.get(PLAYER_ID);
            assertEquals(Player.State.OFFLINE, dirtyPlayer.getState());
            assertEquals(PLAYER_ID, dirtyPlayer.getId());

            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionPlayerMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getPlayerSessionMap().keySet(), hasSize(0));
        }

        @Test
        void testNoExpiredSession() {
            playerStateTracker.removeExpiredSessions();

            assertThat(playerStateTracker.getDirtyPlayersIdMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getSessionPlayerMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerSessionMap().keySet(), hasSize(1));
        }

        @ApplicationScoped
        static class TestMeterRegistry extends SimpleMeterRegistry {}
    }
}
