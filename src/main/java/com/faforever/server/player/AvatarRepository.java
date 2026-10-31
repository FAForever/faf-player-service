package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.Optional;

@Repository
interface AvatarRepository {

    @Transactional
    @Find
    Optional<AvatarEntity> findAvatar(@By(By.ID) int avatarId);
}
