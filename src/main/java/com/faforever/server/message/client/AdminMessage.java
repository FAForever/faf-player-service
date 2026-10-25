package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

public sealed interface AdminMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = AdminMessage.NoticeInfo.class, name = NoticeInfo.COMMAND),})
    sealed interface Server extends AdminMessage, LobbyMessage.Server {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Client.class, name = Client.COMMAND),
    })
    sealed interface Client extends AdminMessage, LobbyMessage.Client {

        String COMMAND = "admin";

        default String command() {
            return COMMAND;
        }

        @JsonProperty("request_id")
        String requestId();

        enum Action {
            @JsonProperty("broadcast") BROADCAST,
            @JsonProperty("closeFA") CLOSE_GAME,
            @JsonProperty("closelobby") CLOSE_LOBBY
        }

        @JsonCreator
        static AdminMessage.Client of(@JsonProperty("request_id") String requestId, @JsonProperty("action") Action action, @JsonProperty("message") @Nullable String message, @JsonProperty("user_id") @Nullable Integer playerId) {
            return switch (action) {
                case BROADCAST -> {
                    if (message == null) {
                        throw new IllegalArgumentException("Missing required property `message`");
                    }
                    yield new BroadcastRequest(requestId, message);
                }
                case CLOSE_GAME -> {
                    if (playerId == null) {
                        throw new IllegalArgumentException("Missing required property `user_id`");
                    }
                    yield new ClosePlayerGameRequest(requestId, playerId);
                }
                case CLOSE_LOBBY -> {
                    if (playerId == null) {
                        throw new IllegalArgumentException("Missing required property `user_id`");
                    }
                    yield new KickPlayerRequest(requestId, playerId);
                }
            };
        }
    }

    record BroadcastRequest(String requestId, String message) implements Client {}

    record ClosePlayerGameRequest(
            String requestId,
            int playerId
    ) implements Client {}

    record KickPlayerRequest(
            String requestId,
            int playerId
    ) implements Client {}

    record NoticeInfo(
            @Nullable String message,
            Style style
    ) implements Server, LobbyMessage.Broadcast {

        public static final String COMMAND = "notice";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    enum Style {
        @JsonProperty("info") INFO,
        @JsonProperty("error") ERROR,
        @JsonProperty("kick") KICK,
        @JsonProperty("kill") KILL
    }

}
