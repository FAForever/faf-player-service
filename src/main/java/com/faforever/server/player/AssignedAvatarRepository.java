package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.domain._AvatarEntity;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
interface AssignedAvatarRepository {

    @Transactional
    @Query("select a.avatar from AssignedAvatarEntity a where a.playerId = :playerId and (a.expirationDate is null or a.expirationDate < current_timestamp)")
    List<AvatarEntity> findAssignedAvatarsByPlayer(@Param("playerId") int playerId);

    @Query("update AssignedAvatarEntity a set a.selected = (a.avatar.url = :avatarUrl) where a.playerId = :playerId")
    void updateSelectedAvatarAssignment(@Param("playerId") int playerId, @Param("avatarUrl") String avatarUrl);

    @Query("update AssignedAvatarEntity a set a.selected = false where a.playerId = :playerId")
    void removeSelectedAvatarAssignment(@Param("playerId") int playerId);

    @Find
    Optional<AvatarEntity> findAvatar(@By(_AvatarEntity.URL) String avatarUrl);

    @Query("update PlayerEntity p set p.selectedAvatar = null where p.id = :playerId")
    void removePlayerAvatar(@Param("playerId") int playerId);

    @Query("update PlayerEntity p set p.selectedAvatar = :avatar where p.id = :playerId")
    void updatePlayerAvatar(@Param("playerId") int playerId, @Param("avatar") AvatarEntity avatar);

    @Transactional
    default AvatarEntity updateSelectedAvatar(int playerId, String avatarUrl) {
        updateSelectedAvatarAssignment(playerId, avatarUrl);
        AvatarEntity avatarEntity = findAvatar(avatarUrl).orElseThrow();
        updatePlayerAvatar(playerId, avatarEntity);
        return avatarEntity;
    }

    @Transactional
    default void removeSelectedAvatar(int playerId) {
        removeSelectedAvatarAssignment(playerId);
        removePlayerAvatar(playerId);
    }

}
