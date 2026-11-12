package com.faforever.server.social;

import com.faforever.server.domain.AvatarEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.captor;
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

    private final AvatarEntity avatarEntity = new AvatarEntity();

    @BeforeEach
    void setup() {
        avatarEntity.setId(5);
        avatarEntity.setUrl("temp");
        avatarEntity.setDescription("temporary");
    }

    @Test
    void testAddRemoveFriend() {
        int otherId = 2;
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest("", otherId, null)));

        ArgumentCaptor<FriendOrFoeEntity> captor = captor();
        verify(socialRepository).upsertPlayerRelationship(captor.capture());

        FriendOrFoeEntity upserted = captor.getValue();
        assertEquals(new FriendOrFoeEntity.Id(PLAYER_ID, otherId), upserted.getId());
        assertEquals(FriendOrFoeEntity.Status.FRIEND, upserted.getStatus());

        socialHandler.handleSocialRequest(
                createInboundMessage(new SocialMessage.SocialRemoveRequest("", otherId, null)));

        verify(socialRepository).deletePlayerRelationship(new FriendOrFoeEntity.Id(PLAYER_ID, otherId));
    }

    @Test
    void testAddRemoveFoe() {
        int otherId = 2;
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest("", null, otherId)));

        ArgumentCaptor<FriendOrFoeEntity> captor = captor();
        verify(socialRepository).upsertPlayerRelationship(captor.capture());

        FriendOrFoeEntity upserted = captor.getValue();
        assertEquals(new FriendOrFoeEntity.Id(PLAYER_ID, otherId), upserted.getId());
        assertEquals(FriendOrFoeEntity.Status.FOE, upserted.getStatus());

        socialHandler.handleSocialRequest(
                createInboundMessage(new SocialMessage.SocialRemoveRequest("", null, otherId)));

        verify(socialRepository).deletePlayerRelationship(new FriendOrFoeEntity.Id(PLAYER_ID, otherId));
    }

    @Test
    void testSendAvatars() {
        when(socialRepository.findAssignedAvatarsByPlayer(PLAYER_ID)).thenReturn(List.of(avatarEntity));

        String requestId = "12345";
        socialHandler.handleSocialRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest(requestId)));

        verify(outboundLobbyMessageEmitter).send(OutboundLobbyMessage.forSession(SESSION_ID,
                                                                                 new SocialMessage.AvatarInfoList(
                                                                                         requestId,
                                                                                         Set.of(new AvatarInfo(
                                                                                                 avatarEntity.getUrl(),
                                                                                                 avatarEntity.getDescription())))));
    }

    @Test
    void testSelectAvatar() {
        int avatarId = 4;
        when(socialRepository.updateSelectedAvatar(PLAYER_ID, "new")).thenReturn(avatarId);

        socialHandler.handleSocialRequest(
                createInboundMessage(new SocialMessage.SelectAvatarRequest("", avatarEntity.getUrl())));

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
