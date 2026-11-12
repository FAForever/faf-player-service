package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AvatarInfo(
        String url,
        @JsonProperty("tooltip") String description
) {}
