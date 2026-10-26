package com.faforever.server.player;

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
import com.faforever.server.rating.Leaderboard;
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
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private static final PlayerInfo PLAYER_INFO = new PlayerInfo(PLAYER_ID, "test", "TEST",
            new AvatarInfo("temp", "temporary"), "",
            Map.of("leaderboard", new LeaderboardStats(1, new LeaderboardStats.Rating(1000, 100))), null);
    private static final Avatar AVATAR = new Avatar(5, "temp", "temporary");

    @Inject
    PlayerStateTracker playerStateTracker;

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

    private final Player player = new Player(PLAYER_ID, "test");

    @BeforeEach
    void setup() {
        player.setClan("TEST");
        player.setAvatar(AVATAR);
        player.updateLeaderboardRatings(List.of(new LeaderboardRating(new Leaderboard("leaderboard"), 1, 1000, 100)));
    }

    @Test
    void testExistingPlayersRegistered() {
        assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));

        verify(playerConnectionsRequestEmitter).send(Map.of());
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(player);
            when(geoIpService.findCountry("127.0.0.1")).thenReturn("");

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.New(
                    Duration.ofSeconds(1))));

            verify(playerRepository).updatePlayerIpAddress(PLAYER_ID, "127.0.0.1");

            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));

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
                    equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan"), Set.of(), Set.of())));

            OutboundLobbyMessage<?> thirdMessage = sentMessages.getLast();
            OutboundTarget thirdTarget = thirdMessage.target();
            assertThat(thirdTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session thirdSessionTarget = (OutboundTarget.Session) thirdTarget;

            assertThat(thirdSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(thirdMessage.message(), isA(SocialMessage.PlayerInfoList.class));
        }

        @Test
        void testRegisterSessionAsModerator() {
            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(player);
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
                    equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan", "#moderators"), Set.of(), Set.of())));
        }

        @Test
        void testNoActionWhenNoDirty() {
            assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));
            playerStateTracker.sendUpdateForDirtyPlayers();
            verifyNoInteractions(outboundLobbyMessageEmitter);
        }

        @Test
        void testUnregisterSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Gone()));

            assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));
        }
    }

    @Nested
    class Registered {

        @BeforeEach
        void setup() {
            when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(player);
            when(geoIpService.findCountry("127.0.0.1")).thenReturn("");

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Existing(
                    Duration.ofMinutes(1))));
        }

        @Test
        void testDoubleRegistrationThrowsWhenDifferentPlayers() {
            when(playerRepository.loadPlayer(2)).thenReturn(new Player(2, "test"));
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

            assertEquals(Player.State.OFFLINE, player.getState());
            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));
        }

        @Test
        void testUnregisterNonRegisteredSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, 2, "127.0.0.1", new PlayerSessionState.State.Gone()));

            assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testUnregisterSessionMultipleSessions() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, 2, "127.0.0.1", new PlayerSessionState.State.New(
                    Duration.ofSeconds(1))));
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Gone()));

            assertEquals(Player.State.ONLINE, player.getState());
        }

        @Test
        void testUpdatePlayer() {
            playerStateTracker.updatePlayer(PLAYER_ID, _ -> {});
            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));

            playerStateTracker.sendUpdateForDirtyPlayers();

            verify(outboundLobbyMessageEmitter).send(
                    OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(Set.of(PLAYER_INFO))));
            assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testUpdateLeaderboards() {
            LeaderboardRating leaderboardRating = new LeaderboardRating(new Leaderboard("leaderboard"), 2, 0, 0);
            when(leaderboardRatingRepository.loadUpdatedLeaderboardRatings(anyCollection(), any())).thenReturn(Map.of(PLAYER_ID, Set.of(
                    leaderboardRating)));

            assertNotEquals(leaderboardRating, player.getLeaderboardRatings().get(leaderboardRating.leaderboard()));

            playerStateTracker.updatePlayerLeaderboardRatings();

            assertEquals(leaderboardRating, player.getLeaderboardRatings().get(leaderboardRating.leaderboard()));
            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));
        }

        @Test
        void testUpdateLeaderboardsNoUpdates() {
            playerStateTracker.updatePlayerLeaderboardRatings();

            assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testClearPlayerAvatar() {
            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, null));

            assertNull(player.getAvatar());
            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));
        }

        @Test
        void testUpdatePlayerAvatar() {
            int avatarId = 2;
            Avatar avatar = new Avatar(avatarId, "temp", "other");
            when(avatarRepository.findAvatar(avatarId)).thenReturn(avatar);

            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, avatarId));

            assertEquals(avatar, player.getAvatar());
            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));
        }

        @Test
        void testExpiredSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, "127.0.0.1", new PlayerSessionState.State.Existing(
                    Duration.ofNanos(-1))));

            playerStateTracker.removeExpiredSessions();

            assertThat(playerStateTracker.getDirtyPlayers(), contains(player));
            assertEquals(Player.State.OFFLINE, player.getState());
        }

        @Test
        void testNoExpiredSession() {
            playerStateTracker.removeExpiredSessions();

            assertThat(playerStateTracker.getDirtyPlayers(), hasSize(0));
            assertEquals(Player.State.ONLINE, player.getState());
        }

        @ApplicationScoped
        static class TestMeterRegistry extends SimpleMeterRegistry {}
    }
}
