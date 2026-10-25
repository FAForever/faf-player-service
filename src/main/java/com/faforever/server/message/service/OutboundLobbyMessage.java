package com.faforever.server.message.service;

import com.faforever.server.message.client.LobbyMessage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record OutboundLobbyMessage<T extends LobbyMessage.Server>(
        OutboundTarget target,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
        T message
) {

    public OutboundLobbyMessage {
        if (target instanceof OutboundTarget.All && !(message instanceof LobbyMessage.Broadcast)) {
            throw new IllegalArgumentException("Only broadcast messages can be sent to all players");
        }
    }

    public static <T extends LobbyMessage.Server> OutboundLobbyMessage<LobbyMessage.Server> forPlayer(int playerId, T message) {
        return new OutboundLobbyMessage<>(new OutboundTarget.Player(playerId), message);
    }

    public static <T extends LobbyMessage.Server> OutboundLobbyMessage<LobbyMessage.Server> forSession(long sessionId, T message) {
        return new OutboundLobbyMessage<>(new OutboundTarget.Session(sessionId), message);
    }

    public static <T extends LobbyMessage.Broadcast> OutboundLobbyMessage<LobbyMessage.Server> forAll(T message) {
        return new OutboundLobbyMessage<>(new OutboundTarget.All(), message);
    }
}
