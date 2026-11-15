package com.faforever.server.player.dto;

import com.faforever.server.domain.FriendOrFoeEntity;

public record FriendOrFoe(
        FriendOrFoeEntity.Status status,
        int subjectId
) {
}
