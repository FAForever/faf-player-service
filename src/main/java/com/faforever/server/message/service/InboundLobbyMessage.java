package com.faforever.server.message.service;

import com.faforever.server.message.client.LobbyMessage;

public record InboundLobbyMessage<T extends LobbyMessage.Client>(
        long sessionId,
        int playerId,
        T message
) {}
