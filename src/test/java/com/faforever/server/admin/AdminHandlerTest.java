package com.faforever.server.admin;

import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class AdminHandlerTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;

    @Inject
    AdminHandler socialService;

    @InjectMock
    private Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;
    @InjectMock
    private AdminRepository adminRepository;

    @Test
    void testBroadcast() {
        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("", "test")));

        verify(outboundLobbyMessageEmitter).send(
                OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO)));
    }

    @Test
    void testBroadcastNoMessage() {
        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("", "")));

        verifyNoInteractions(adminRepository, outboundLobbyMessageEmitter);
    }

    @Test
    void testBroadcastNoPermission() {
        when(adminRepository.playerLacksPermission(PLAYER_ID, "ADMIN_BROADCAST_MESSAGE")).thenReturn(
                true);

        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("", "test")));

        verifyNoInteractions(outboundLobbyMessageEmitter);
    }

    @Test
    void testKick() {
        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest("", 2)));

        verify(outboundLobbyMessageEmitter).send(
                OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)));
    }

    @Test
    void testKickNoPermission() {
        when(adminRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest("", 2)));

        verifyNoInteractions(outboundLobbyMessageEmitter);
    }

    @Test
    void testCloseGame() {
        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest("", 2)));

        verify(outboundLobbyMessageEmitter).send(
                OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)));
    }

    @Test
    void testCloseGameNoPermission() {
        when(adminRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        socialService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest("", 2)));

        verifyNoInteractions(outboundLobbyMessageEmitter);
    }

    private <T extends LobbyMessage.Client> InboundLobbyMessage<T> createInboundMessage(T message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
