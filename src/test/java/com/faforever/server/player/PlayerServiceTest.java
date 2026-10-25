package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class PlayerServiceTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;
    private static final Avatar AVATAR = new Avatar(5, "temp", "temporary");

    @Inject
    PlayerService playerService;

    @InjectMock
    private Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;
    @InjectMock
    private Emitter<PlayerAvatarUpdate> outboundAvatarUpdateEmitter;

    @InjectMock
    private FriendOrFoeRepository friendOrFoeRepository;
    @InjectMock
    private AssignedAvatarRepository assignedAvatarRepository;
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
    void testAddRemoveFriend() {
        int otherId = 2;
        playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest("", otherId, null)));

        verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FRIEND);

        playerService.handleSocialRequest(
                createInboundMessage(new SocialMessage.SocialRemoveRequest("", otherId, null)));

        verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
    }

    @Test
    void testAddRemoveFoe() {
        int otherId = 2;
        playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest("", null, otherId)));

        verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);

        playerService.handleSocialRequest(
                createInboundMessage(new SocialMessage.SocialRemoveRequest("", null, otherId)));

        verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
    }

    @Test
    void testSendAvatars() {
        when(assignedAvatarRepository.findAssignedAvatarsByPlayer(PLAYER_ID)).thenReturn(Set.of(AVATAR));

        String requestId = "12345";
        playerService.handleSocialRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest(requestId)));

        verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                 new SocialMessage.AvatarInfoList(
                                                                                         requestId,
                                                                                         Set.of(new AvatarInfo(
                                                                                                 AVATAR.url(),
                                                                                                 AVATAR.description())))));
    }

    @Test
    void testSelectAvatar() {
        Avatar avatar = new Avatar(4, "new", "newone");

        when(assignedAvatarRepository.updateSelectedAvatar(PLAYER_ID, "new")).thenReturn(avatar);

        playerService.handleSocialRequest(
                createInboundMessage(new SocialMessage.SelectAvatarRequest("", avatar.url())));

        verify(outboundAvatarUpdateEmitter).send(new PlayerAvatarUpdate(PLAYER_ID, 4));
    }

    @Test
    void testRemoveAvatar() {
        playerService.handleSocialRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest("")));

        verify(assignedAvatarRepository).removeSelectedAvatar(PLAYER_ID);
        verify(outboundAvatarUpdateEmitter).send(new PlayerAvatarUpdate(PLAYER_ID, null));
    }

    @Test
    void testBroadcast() {
        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("", "test")));

        verify(outboundLobbyMessageEmitter).send(
                OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO)));
    }

    @Test
    void testBroadcastNoMessage() {
        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("", "")));

        verifyNoInteractions(userGroupAssignmentRepository, outboundLobbyMessageEmitter);
    }

    @Test
    void testBroadcastNoPermission() {
        when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_BROADCAST_MESSAGE")).thenReturn(
                true);

        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("", "test")));

        verifyNoInteractions(outboundLobbyMessageEmitter);
    }

    @Test
    void testKick() {
        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest("", 2)));

        verify(outboundLobbyMessageEmitter).send(
                OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)));
    }

    @Test
    void testKickNoPermission() {
        when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest("", 2)));

        verifyNoInteractions(outboundLobbyMessageEmitter);
    }

    @Test
    void testCloseGame() {
        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest("", 2)));

        verify(outboundLobbyMessageEmitter).send(
                OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)));
    }

    @Test
    void testCloseGameNoPermission() {
        when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        playerService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest("", 2)));

        verifyNoInteractions(outboundLobbyMessageEmitter);
    }

    private <T extends LobbyMessage.Client> InboundLobbyMessage<T> createInboundMessage(T message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
