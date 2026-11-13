package com.faforever.server.social;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class SocialHandlerTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;

    @Inject
    SocialHandler socialHandler;

    @InjectMock
    private Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;
    @InjectMock
    private Emitter<PlayerAvatarUpdate> outboundAvatarUpdateEmitter;

    @InjectMock
    private SocialRepository socialRepository;

    @Test
    void testAddRemoveFriend() {
        int otherId = 2;
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest("", otherId, null)));

        verify(socialRepository).upsertPlayerRelationship(assertArg(friendOrFoeEntity -> {
            assertEquals(new FriendOrFoeEntity.Id(PLAYER_ID, otherId), friendOrFoeEntity.getId());
            assertEquals(FriendOrFoeEntity.Status.FRIEND, friendOrFoeEntity.getStatus());
        }));

        socialHandler.handleSocialRequest(
                createInboundMessage(new SocialMessage.SocialRemoveRequest("", otherId, null)));

        verify(socialRepository).deletePlayerRelationship(new FriendOrFoeEntity.Id(PLAYER_ID, otherId));
    }

    @Test
    void testAddRemoveFoe() {
        int otherId = 2;
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest("", null, otherId)));

        verify(socialRepository).upsertPlayerRelationship(assertArg(friendOrFoeEntity -> {
            assertEquals(new FriendOrFoeEntity.Id(PLAYER_ID, otherId), friendOrFoeEntity.getId());
            assertEquals(FriendOrFoeEntity.Status.FOE, friendOrFoeEntity.getStatus());
        }));

        socialHandler.handleSocialRequest(
                createInboundMessage(new SocialMessage.SocialRemoveRequest("", null, otherId)));

        verify(socialRepository).deletePlayerRelationship(new FriendOrFoeEntity.Id(PLAYER_ID, otherId));
    }

    @Test
    void testSendAvatars() {
        String url = "temp";
        String description = "temporary";

        when(socialRepository.findAssignedAvatarsByPlayer(PLAYER_ID)).thenReturn(
                List.of(new AvatarInfo(url, description)));

        String requestId = "12345";
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest(requestId)));

        verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                 new SocialMessage.AvatarInfoList(
                                                                                         requestId,
                                                                                         List.of(new AvatarInfo(url,
                                                                                                                description)))));
    }

    @Test
    void testSelectAvatar() {
        int avatarId = 4;
        String url = "new";
        when(socialRepository.updateSelectedAvatar(PLAYER_ID, url)).thenReturn(avatarId);

        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.SelectAvatarRequest("", url)));

        verify(outboundAvatarUpdateEmitter).send(new PlayerAvatarUpdate(PLAYER_ID, avatarId));
    }

    @Test
    void testRemoveAvatar() {
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest("")));

        verify(socialRepository).removeSelectedAvatar(PLAYER_ID);
        verify(outboundAvatarUpdateEmitter).send(new PlayerAvatarUpdate(PLAYER_ID, null));
    }

    private <T extends LobbyMessage.Client> InboundLobbyMessage<T> createInboundMessage(T message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
