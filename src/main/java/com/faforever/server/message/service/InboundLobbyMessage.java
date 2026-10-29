package com.faforever.server.message.service;

import com.faforever.server.message.client.LobbyMessage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record InboundLobbyMessage<T extends LobbyMessage.Client>(
        long sessionId,
        int playerId,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
        T message
) {}
