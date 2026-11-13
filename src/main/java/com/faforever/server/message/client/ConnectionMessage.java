package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.PlayerInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

public sealed interface ConnectionMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = ConnectionMessage.LoginSuccessResponse.class, name = LoginSuccessResponse.COMMAND),})
    sealed interface Server extends ConnectionMessage, LobbyMessage.Server {}

    record LoginSuccessResponse(
            PlayerInfo me,
            Instant currentTime
    ) implements Server {
        public static final String COMMAND = "welcome";

        @Override
        public String command() {
            return COMMAND;
        }
    }

}
