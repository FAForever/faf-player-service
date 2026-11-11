package com.faforever.server.admin;

import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.micrometer.InboundMessageCommandResolver;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
class AdminHandler {

    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;

    private final AdminRepository adminRepository;

    AdminHandler(
            @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
            AdminRepository adminRepository) {
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.adminRepository = adminRepository;
    }

    @Incoming("admin-inbound")
    @MDCPopulated
    @Timed(value = "faf.request", extraTags = {"type", "admin"}, histogram = true)
    void handleAdminRequest(@MeterTag(value = "command",
                                      resolver = InboundMessageCommandResolver.class) InboundLobbyMessage<AdminMessage.Client> request) {
        switch (request.message()) {
            case AdminMessage.BroadcastRequest broadcastRequest -> broadcast(request.playerId(), broadcastRequest);
            case AdminMessage.KickPlayerRequest kickPlayerRequest -> kickPlayer(request.playerId(), kickPlayerRequest);
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest ->
                    closePlayerGame(request.playerId(), closePlayerGameRequest);
        }
    }

    private void broadcast(int playerId, AdminMessage.BroadcastRequest broadcastRequest) {
        String message = broadcastRequest.message();
        if (message.isBlank()) {
            LOG.warn("Received broadcast with blank message");
            return;
        }

        if (adminRepository.playerLacksPermission(playerId, "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warn("Unauthorized broadcast request: {}", broadcastRequest.message());
            return;
        }

        LOG.info("Sending broad cast message: {}", message);
        outboundLobbyMessageEmitter.send(
                OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO)));
    }

    private void kickPlayer(int playerId, AdminMessage.KickPlayerRequest kickPlayerRequest) {
        if (adminRepository.playerLacksPermission(playerId, "ADMIN_KICK_SERVER")) {
            LOG.warn("Unauthorized kick request with target: {}", kickPlayerRequest.playerId());
            return;
        }

        LOG.info("Kicking player `{}`", kickPlayerRequest.playerId());
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(kickPlayerRequest.playerId(),
                                                                        new AdminMessage.NoticeInfo(null,
                                                                                                    AdminMessage.Style.KICK)));
    }

    private void closePlayerGame(int playerId, AdminMessage.ClosePlayerGameRequest closePlayerGameRequest) {
        if (adminRepository.playerLacksPermission(playerId, "ADMIN_KICK_SERVER")) {
            LOG.warn("Unauthorized close request with target: {}", closePlayerGameRequest.playerId());
            return;
        }

        LOG.info("Closing lobby for player `{}`", closePlayerGameRequest.playerId());
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(closePlayerGameRequest.playerId(),
                                                                        new AdminMessage.NoticeInfo(null,
                                                                                                    AdminMessage.Style.KILL)));
    }

}
