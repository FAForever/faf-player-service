package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public sealed interface SocialMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = SocialMessage.PlayerInfoList.class, name = PlayerInfoList.COMMAND),
                   @JsonSubTypes.Type(value = SocialMessage.AvatarInfoList.class, name = AvatarInfoList.COMMAND),
                   @JsonSubTypes.Type(value = SocialMessage.SocialInfo.class, name = SocialInfo.COMMAND),
    })
    sealed interface Server extends SocialMessage, LobbyMessage.Server {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = SocialAddRequest.class, name = SocialAddRequest.COMMAND),
                   @JsonSubTypes.Type(value = SocialRemoveRequest.class, name = SocialRemoveRequest.COMMAND),
                   @JsonSubTypes.Type(value = AvatarRequest.class, name = AvatarRequest.COMMAND),})
    sealed interface Client extends SocialMessage, LobbyMessage.Client {}

    record PlayerInfoList(
            Set<PlayerInfo> players
    ) implements Server, LobbyMessage.Broadcast {
        public static final String COMMAND = "player_info";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record AvatarInfoList(
            String requestId,
            @JsonProperty("avatarlist") Set<AvatarInfo> avatars
    ) implements Server {
        public static final String COMMAND = "avatar";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record SocialInfo(
            @JsonProperty("autojoin")Collection<String> channels,
            @JsonProperty("friends") Collection<Integer> friendIds,
            @JsonProperty("foes") Collection<Integer> foeIds
    ) implements Server {
        public static final String COMMAND = "social";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record SocialAddRequest(
            String requestId,
            @JsonProperty("friend") @Nullable Integer friendId,
            @JsonProperty("foe") @Nullable Integer foeId
    ) implements Client {
        public static final String COMMAND = "social_add";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record SocialRemoveRequest(
            String requestId,
            @JsonProperty("friend") @Nullable Integer friendId,
            @JsonProperty("foe") @Nullable Integer foeId
    ) implements Client {
        public static final String COMMAND = "social_remove";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    sealed interface AvatarRequest extends Client {
        String COMMAND = "avatar";

        @Override
        default String command() {
            return COMMAND;
        }

        @JsonProperty("request_id")
        String requestId();

        enum Action {
            @JsonProperty("select") SELECT,
            @JsonProperty("list_avatar") LIST_AVATAR
        }

        @JsonCreator
        static AvatarRequest of(@JsonProperty("request_id") String requestId, @JsonProperty("action") Action action, @JsonProperty("avatar") @Nullable String avatarUrl) {
            return switch (action) {
                case LIST_AVATAR -> new ListAvatarsRequest(requestId);
                case SELECT ->
                        avatarUrl == null ? new RemoveAvatarRequest(requestId) : new SelectAvatarRequest(requestId, avatarUrl);
            };
        }

    }

    record ListAvatarsRequest(String requestId) implements AvatarRequest {}

    record SelectAvatarRequest(
            String requestId,
            String avatarUrl
    ) implements AvatarRequest {}

    record RemoveAvatarRequest(String requestId) implements AvatarRequest {}
}
