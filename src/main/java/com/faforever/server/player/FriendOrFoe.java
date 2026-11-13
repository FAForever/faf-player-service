package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;

record FriendOrFoe(
        FriendOrFoeEntity.Status status,
        int subjectId
) {
}
