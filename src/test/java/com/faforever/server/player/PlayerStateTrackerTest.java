package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.geoip.GeoIpService;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.LeaderboardStats;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.message.service.PlayerSessionState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class PlayerStateTrackerTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;
    private static final String PLAYER_NAME = "test";
    private static final String CLAN_TAG = "TEST";
    private static final String AVATAR_URL = "temp";
    private static final String AVATAR_DESCRIPTION = "temporary";
    private static final String LEADERBOARD = "leaderboard";
    private static final int LEADERBOARD_MEAN = 1000;
    private static final int LEADERBOARD_DEVIATION = 100;
    private static final LeaderboardStats.Rating LEADERBOARD_RATING = new LeaderboardStats.Rating(LEADERBOARD_MEAN,
                                                                                                  LEADERBOARD_DEVIATION);
    private static final int LEADERBOARD_NUMBER_OF_GAMES = 1;
    private static final LeaderboardStats LEADERBOARD_STATS = new LeaderboardStats(LEADERBOARD_NUMBER_OF_GAMES,
                                                                                   LEADERBOARD_RATING);
    private static final AvatarInfo AVATAR_INFO = new AvatarInfo(AVATAR_URL, AVATAR_DESCRIPTION);

    private static final Avatar AVATAR = new Avatar(AVATAR_URL, AVATAR_DESCRIPTION);
    private static final String IP_ADDRESS = "127.0.0.1";
    private static final Instant INSTANT = Instant.now();
    private static final String COUNTRY = "USA";

    @Inject
    PlayerStateTracker playerStateTracker;

    @InjectMock
    private GeoIpService geoIpService;
    @InjectMock
    private PlayerStateRepository playerStateRepository;
    @InjectMock
    private Emitter<Map<String, String>> playerConnectionsRequestEmitter;
    @InjectMock
    private Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;

    @Test
    void testExistingPlayersRegistered() {
        assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));

        verify(playerConnectionsRequestEmitter).send(Map.of());
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            Player player = new Player(PLAYER_ID, PLAYER_NAME, null, null, null);
            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(player));

            when(geoIpService.findCountry(IP_ADDRESS)).thenReturn(COUNTRY);

            Duration secondsToExpiry = Duration.ofSeconds(1);
            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        secondsToExpiry)));

            verify(playerStateRepository).updatePlayerIpAddress(PLAYER_ID, IP_ADDRESS);

            assertTrue(playerStateTracker.getPlayerIdMap().containsKey(PLAYER_ID));
            PlayerInfo expectedPlayerInfo = new PlayerInfo(PLAYER_ID, PLAYER_NAME, null, null, COUNTRY, Map.of(), null);

            Map<Integer, PlayerInfo> dirtyPlayers = playerStateTracker.getDirtyPlayerInfoIdMap();
            assertEquals(expectedPlayerInfo, dirtyPlayers.get(PLAYER_ID));

            ConnectionMessage.LoginSuccessResponse expectedLoginResponse = new ConnectionMessage.LoginSuccessResponse(
                    expectedPlayerInfo, INSTANT);
            verify(outboundLobbyMessageEmitter).send(
                    OutboundLobbyMessage.forSession(SESSION_ID, expectedLoginResponse));

            SocialMessage.SocialInfo expectedSocialInfo = new SocialMessage.SocialInfo(Set.of("#aeolus"), Set.of(),
                                                                                       Set.of());
            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID, expectedSocialInfo));

            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                     new SocialMessage.PlayerInfoList(
                                                                                             Set.of(expectedPlayerInfo))));

            assertEquals(INSTANT.plus(secondsToExpiry), playerStateTracker.getSessionExpirationMap().get(SESSION_ID));
            assertEquals(player, playerStateTracker.getPlayerIdMap().get(PLAYER_ID));
            assertEquals(PLAYER_ID, playerStateTracker.getSessionPlayerIdMap().get(SESSION_ID));
            assertEquals(Set.of(SESSION_ID), playerStateTracker.getPlayerIdSessionMap().get(PLAYER_ID));
        }

        @Test
        void testRegisterSessionAsModerator() {
            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(
                    Optional.of(new Player(PLAYER_ID, PLAYER_NAME, null, null, null)));
            when(geoIpService.findCountry(IP_ADDRESS)).thenReturn(COUNTRY);

            when(playerStateRepository.isPlayerModerator(PLAYER_ID)).thenReturn(true);

            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        Duration.ofSeconds(1))));

            SocialMessage.SocialInfo expectedSocialInfo = new SocialMessage.SocialInfo(Set.of("#aeolus", "#moderators"),
                                                                                       Set.of(), Set.of());
            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID, expectedSocialInfo));
        }

        @Test
        void testRegisterSessionClan() {
            Player clanPlayer = new Player(PLAYER_ID, PLAYER_NAME, CLAN_TAG, null, null);

            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(clanPlayer));
            when(geoIpService.findCountry(IP_ADDRESS)).thenReturn(COUNTRY);

            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        Duration.ofSeconds(1))));

            PlayerInfo expectedPlayerInfo = new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, null, COUNTRY, Map.of(),
                                                           null);
            ConnectionMessage.LoginSuccessResponse expectedLoginResponse = new ConnectionMessage.LoginSuccessResponse(
                    expectedPlayerInfo, INSTANT);
            verify(outboundLobbyMessageEmitter).send(
                    OutboundLobbyMessage.forSession(SESSION_ID, expectedLoginResponse));

            SocialMessage.SocialInfo expectedSocialInfo = new SocialMessage.SocialInfo(Set.of("#aeolus", "#TEST_clan"),
                                                                                       Set.of(), Set.of());
            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID, expectedSocialInfo));

            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                     new SocialMessage.PlayerInfoList(
                                                                                             Set.of(expectedPlayerInfo))));
        }

        @Test
        void testRegisterSessionAvatar() {
            Player player = new Player(PLAYER_ID, PLAYER_NAME, null, AVATAR_URL, AVATAR_DESCRIPTION);
            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(player));
            when(geoIpService.findCountry(IP_ADDRESS)).thenReturn(COUNTRY);

            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        Duration.ofSeconds(1))));

            assertTrue(playerStateTracker.getPlayerIdMap().containsKey(PLAYER_ID));

            PlayerInfo expectedPlayerInfo = new PlayerInfo(PLAYER_ID, PLAYER_NAME, null, AVATAR_INFO, COUNTRY, Map.of(),
                                                           null);
            Map<Integer, PlayerInfo> dirtyPlayers = playerStateTracker.getDirtyPlayerInfoIdMap();
            assertEquals(expectedPlayerInfo, dirtyPlayers.get(PLAYER_ID));
            assertEquals(AVATAR, player.getAvatar());

            ConnectionMessage.LoginSuccessResponse expectedLoginResponse = new ConnectionMessage.LoginSuccessResponse(
                    expectedPlayerInfo, INSTANT);
            verify(outboundLobbyMessageEmitter).send(
                    OutboundLobbyMessage.forSession(SESSION_ID, expectedLoginResponse));

            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                     new SocialMessage.PlayerInfoList(
                                                                                             Set.of(expectedPlayerInfo))));
        }

        @Test
        void testRegisterSessionFriendAndFoe() {
            when(playerStateRepository.loadFriendsAndFoes(PLAYER_ID)).thenReturn(
                    List.of(new FriendOrFoe(FriendOrFoeEntity.Status.FRIEND, 3),
                            new FriendOrFoe(FriendOrFoeEntity.Status.FOE, 4)));

            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(
                    Optional.of(new Player(PLAYER_ID, PLAYER_NAME, null, null, null)));


            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        Duration.ofSeconds(1))));

            SocialMessage.SocialInfo expectedSocialInfo = new SocialMessage.SocialInfo(Set.of("#aeolus"), Set.of(3),
                                                                                       Set.of(4));
            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID, expectedSocialInfo));
        }

        @Test
        void testRegisterSessionLeaderboardRating() {
            Player player = new Player(PLAYER_ID, PLAYER_NAME, null, null, null);
            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(player));
            when(playerStateRepository.loadPlayerRatings(PLAYER_ID)).thenReturn(
                    List.of(new LeaderboardRating(LEADERBOARD, LEADERBOARD_NUMBER_OF_GAMES, LEADERBOARD_MEAN,
                                                  LEADERBOARD_DEVIATION)));
            when(geoIpService.findCountry(IP_ADDRESS)).thenReturn(COUNTRY);

            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        Duration.ofSeconds(1))));

            verify(playerStateRepository).updatePlayerIpAddress(PLAYER_ID, IP_ADDRESS);

            Map<Integer, PlayerInfo> dirtyPlayers = playerStateTracker.getDirtyPlayerInfoIdMap();
            PlayerInfo expectedPlayerInfo = new PlayerInfo(PLAYER_ID, PLAYER_NAME, null, null, COUNTRY,
                                                           Map.of(LEADERBOARD, LEADERBOARD_STATS), null);
            assertEquals(expectedPlayerInfo, dirtyPlayers.get(PLAYER_ID));

            assertEquals(Set.of(new LeaderboardRating(LEADERBOARD, LEADERBOARD_NUMBER_OF_GAMES, LEADERBOARD_MEAN,
                                                      LEADERBOARD_DEVIATION)), player.getLeaderboardRatings());

            ConnectionMessage.LoginSuccessResponse expectedLoginResponse = new ConnectionMessage.LoginSuccessResponse(
                    expectedPlayerInfo, INSTANT);
            verify(outboundLobbyMessageEmitter).send(
                    OutboundLobbyMessage.forSession(SESSION_ID, expectedLoginResponse));

            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                     new SocialMessage.PlayerInfoList(
                                                                                             Set.of(expectedPlayerInfo))));
        }

        @Test
        void testRegisterSessionBadId() {
            assertThrows(IllegalStateException.class, () -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.New(Duration.ofSeconds(1)))));
        }

        @Test
        void testNoActionWhenNoDirty() {
            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
            playerStateTracker.sendUpdateForDirtyPlayers();
            verifyNoInteractions(outboundLobbyMessageEmitter);
        }

        @Test
        void testUnregisterSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUpdateLeaderboardsNoPlayers() {
            playerStateTracker.updatePlayerLeaderboardRatings();

            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUpdatePlayerAvatar() {
            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, 2));

            Map<Integer, PlayerInfo> dirtyPlayers = playerStateTracker.getDirtyPlayerInfoIdMap();
            assertThat(dirtyPlayers.keySet(), hasSize(0));

            verifyNoInteractions(playerStateRepository);
        }

    }

    @Nested
    class Registered {

        final Player player = new Player(PLAYER_ID, PLAYER_NAME, CLAN_TAG, AVATAR_URL, AVATAR_DESCRIPTION);

        @BeforeEach
        void setup() {
            when(playerStateRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(player));
            when(playerStateRepository.loadPlayerRatings(PLAYER_ID)).thenReturn(
                    List.of(new LeaderboardRating(LEADERBOARD, LEADERBOARD_NUMBER_OF_GAMES, LEADERBOARD_MEAN,
                                                  LEADERBOARD_DEVIATION)));
            when(geoIpService.findCountry(IP_ADDRESS)).thenReturn(COUNTRY);

            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.Existing(
                                                                                        Duration.ofSeconds(1))));
        }

        @Test
        void testDoubleRegistrationThrowsWhenDifferentPlayers() {
            when(playerStateRepository.loadPlayer(2)).thenReturn(
                    Optional.of(new Player(PLAYER_ID + 2, "other", null, null, null)));
            assertThrows(IllegalStateException.class, () -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(2, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.New(Duration.ofSeconds(1)))));
            assertThrows(IllegalStateException.class, () -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(2, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.Existing(Duration.ofSeconds(1)))));
        }

        @Test
        void testDoubleRegistrationDoesNotThrowWhenSamePlayer() {
            assertDoesNotThrow(() -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.New(Duration.ofSeconds(1)))));
            assertDoesNotThrow(() -> playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.Existing(Duration.ofSeconds(1)))));
        }

        @Test
        void testUnregisterSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertFalse(playerStateTracker.getPlayerIdMap().containsKey(PLAYER_ID));
            assertFalse(playerStateTracker.getSessionExpirationMap().containsKey(SESSION_ID));
            assertFalse(playerStateTracker.getSessionPlayerIdMap().containsKey(SESSION_ID));
            assertFalse(playerStateTracker.getPlayerIdSessionMap().containsKey(PLAYER_ID));

            playerStateTracker.sendUpdateForDirtyPlayers();
            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(
                    Set.of(new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, AVATAR_INFO, COUNTRY,
                                          Map.of(LEADERBOARD, LEADERBOARD_STATS), PlayerInfo.State.OFFLINE)))));
        }

        @Test
        void testUnregisterNonRegisteredSession() {
            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, 2, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
            assertTrue(playerStateTracker.getPlayerIdMap().containsKey(PLAYER_ID));
            assertTrue(playerStateTracker.getSessionPlayerIdMap().containsKey(SESSION_ID));
            assertTrue(playerStateTracker.getPlayerIdSessionMap().containsKey(PLAYER_ID));
        }

        @Test
        void testUnregisterSessionMultipleSessions() {
            Duration secondsToExpiry = Duration.ofSeconds(1);
            long newSessionId = 2;
            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, newSessionId, IP_ADDRESS,
                                                                                new PlayerSessionState.State.New(
                                                                                        secondsToExpiry)));

            assertTrue(playerStateTracker.getDirtyPlayerInfoIdMap().containsKey(PLAYER_ID));

            Instant expirationTime = INSTANT.plus(secondsToExpiry);
            assertEquals(expirationTime, playerStateTracker.getSessionExpirationMap().get(SESSION_ID));
            assertEquals(expirationTime, playerStateTracker.getSessionExpirationMap().get(newSessionId));
            assertEquals(player, playerStateTracker.getPlayerIdMap().get(PLAYER_ID));
            assertEquals(PLAYER_ID, playerStateTracker.getSessionPlayerIdMap().get(SESSION_ID));
            assertEquals(PLAYER_ID, playerStateTracker.getSessionPlayerIdMap().get(newSessionId));
            assertEquals(Set.of(SESSION_ID, newSessionId), playerStateTracker.getPlayerIdSessionMap().get(PLAYER_ID));

            playerStateTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertTrue(playerStateTracker.getDirtyPlayerInfoIdMap().containsKey(PLAYER_ID));

            assertTrue(playerStateTracker.getPlayerIdMap().containsKey(PLAYER_ID));
            assertEquals(expirationTime, playerStateTracker.getSessionExpirationMap().get(newSessionId));
            assertFalse(playerStateTracker.getSessionExpirationMap().containsKey(SESSION_ID));
            assertEquals(player, playerStateTracker.getPlayerIdMap().get(PLAYER_ID));
            assertFalse(playerStateTracker.getSessionPlayerIdMap().containsKey(SESSION_ID));
            assertEquals(PLAYER_ID, playerStateTracker.getSessionPlayerIdMap().get(newSessionId));
            assertEquals(Set.of(newSessionId), playerStateTracker.getPlayerIdSessionMap().get(PLAYER_ID));
        }

        @Test
        void testUpdatePlayer() {
            playerStateTracker.updatePlayer(PLAYER_ID, _ -> {});
            assertTrue(playerStateTracker.getDirtyPlayerInfoIdMap().containsKey(PLAYER_ID));

            playerStateTracker.sendUpdateForDirtyPlayers();

            verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(
                    Set.of(new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, AVATAR_INFO, COUNTRY,
                                          Map.of(LEADERBOARD, LEADERBOARD_STATS), null)))));
            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
        }

        @Test
        void testUpdateLeaderboards() {
            String leaderboard = LEADERBOARD;
            double deviation = 10d;
            double mean = 100d;
            int totalGames = 2;

            when(playerStateRepository.loadUpdatedLeaderboardRatings(anyCollection(), any())).thenReturn(
                    List.of(new PlayerLeaderboardRating(PLAYER_ID, new LeaderboardRating(leaderboard, totalGames, mean,
                                                                                         deviation))));

            playerStateTracker.updatePlayerLeaderboardRatings();

            assertEquals(new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, AVATAR_INFO, COUNTRY,
                                        Map.of(LEADERBOARD,
                                               new LeaderboardStats(2, new LeaderboardStats.Rating(100, 10))), null),
                         playerStateTracker.getDirtyPlayerInfoIdMap().get(PLAYER_ID));
        }

        @Test
        void testUpdateLeaderboardsNoUpdates() {
            playerStateTracker.updatePlayerLeaderboardRatings();

            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
        }

        @Test
        void testClearPlayerAvatar() {
            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, null));

            assertEquals(new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, null, COUNTRY,
                                        Map.of(LEADERBOARD, LEADERBOARD_STATS), null),
                         playerStateTracker.getDirtyPlayerInfoIdMap().get(PLAYER_ID));
        }

        @Test
        void testUpdatePlayerAvatar() {
            int avatarId = 2;
            String url = "other";
            String description = "other avatar";
            when(playerStateRepository.findAvatar(avatarId)).thenReturn(Optional.of(new Avatar(url, description)));

            playerStateTracker.handlePlayerAvatar(new PlayerAvatarUpdate(PLAYER_ID, avatarId));

            assertEquals(new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, new AvatarInfo(url, description), COUNTRY,
                                        Map.of(LEADERBOARD, LEADERBOARD_STATS), null),
                         playerStateTracker.getDirtyPlayerInfoIdMap().get(PLAYER_ID));
        }

        @Test
        void testExpiredSession() {
            playerStateTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                                new PlayerSessionState.State.Existing(
                                                                                        Duration.ofNanos(-1))));

            playerStateTracker.removeExpiredSessions();

            assertFalse(playerStateTracker.getPlayerIdMap().containsKey(PLAYER_ID));

            assertEquals(new PlayerInfo(PLAYER_ID, PLAYER_NAME, CLAN_TAG, AVATAR_INFO, COUNTRY,
                                        Map.of(LEADERBOARD, LEADERBOARD_STATS), PlayerInfo.State.OFFLINE),
                         playerStateTracker.getDirtyPlayerInfoIdMap().get(PLAYER_ID));

            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionPlayerIdMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getPlayerIdSessionMap().keySet(), hasSize(0));
        }

        @Test
        void testNoExpiredSession() {
            playerStateTracker.removeExpiredSessions();

            assertThat(playerStateTracker.getDirtyPlayerInfoIdMap().keySet(), hasSize(0));
            assertThat(playerStateTracker.getSessionExpirationMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerIdMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getSessionPlayerIdMap().keySet(), hasSize(1));
            assertThat(playerStateTracker.getPlayerIdSessionMap().keySet(), hasSize(1));
        }
    }

    @ApplicationScoped
    static class TestMeterRegistry extends SimpleMeterRegistry {}

    static class ClockProducer {
        @Produces
        Clock clock() {
            return Clock.fixed(INSTANT, ZoneOffset.UTC);
        }
    }
}
