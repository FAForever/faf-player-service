package com.faforever.server.social;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.dto.AvatarInfo;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
interface SocialRepository {

    @Transactional
    @Query("select new AvatarInfo(a.avatar.url, a.avatar.description) from AssignedAvatarEntity a where a.playerId = :playerId and (a.expirationDate is null or a.expirationDate < current_timestamp)")
    List<AvatarInfo> findAssignedAvatarsByPlayer(int playerId);

    @Query("select a.avatar.id from AssignedAvatarEntity a where a.playerId = :playerId and a.avatar.url = :avatarUrl and (a.expirationDate is null or a.expirationDate < current_timestamp)")
    Optional<Integer> findAssignedAvatarIdByPlayer(int playerId,
                                                   String avatarUrl);

    @Query("update AssignedAvatarEntity a set a.selected = (a.avatar.url = :avatarUrl) where a.playerId = :playerId")
    void updateSelectedAvatarAssignment(int playerId, String avatarUrl);

    @Query("update AssignedAvatarEntity a set a.selected = false where a.playerId = :playerId")
    void removeSelectedAvatarAssignment(int playerId);

    @Query("update PlayerEntity p set p.selectedAvatar = null where p.id = :playerId")
    void removePlayerAvatar(int playerId);

    @Query("update PlayerEntity p set p.selectedAvatar.id = :avatarId where p.id = :playerId")
    void updatePlayerAvatar(int playerId, int avatarId);

    @Transactional
    default int updateSelectedAvatar(int playerId, String avatarUrl) {
        int avatarId = findAssignedAvatarIdByPlayer(playerId, avatarUrl).orElseThrow();
        updateSelectedAvatarAssignment(playerId, avatarUrl);
        updatePlayerAvatar(playerId, avatarId);
        return avatarId;
    }

    @Transactional
    default void removeSelectedAvatar(int playerId) {
        removeSelectedAvatarAssignment(playerId);
        removePlayerAvatar(playerId);
    }

    @Save
    @Transactional
    void upsertPlayerRelationship(FriendOrFoeEntity entity);

    @Delete
    @Transactional
    void deletePlayerRelationship(@By(By.ID) FriendOrFoeEntity.Id id);

}
