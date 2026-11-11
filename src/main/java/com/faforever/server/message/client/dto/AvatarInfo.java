package com.faforever.server.message.client.dto;

import com.faforever.server.social.Avatar;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AvatarInfo(
        String url,
        @JsonProperty("tooltip") String description
) {

    public static AvatarInfo from(Avatar avatar) {
        return new AvatarInfo(avatar.url(), avatar.description());
    }

}
