package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record PlayerInfo(
        int id,
        String login,
        @Nullable String clan,
        @Nullable AvatarInfo avatar,
        String country,
        Map<String, LeaderboardStats> ratings,
        @Nullable State state
) {

    public enum State {
        @JsonProperty("offline") OFFLINE
    }

}
