package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;

public record Avatar(
        int id,
        String url,
        String description
) {

    public static Avatar from(AvatarEntity entity) {
        return new Avatar(entity.getId(), entity.getUrl(), entity.getDescription());
    }
}
