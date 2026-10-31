package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain._FriendOrFoeEntity;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import jakarta.transaction.Transactional;

import java.util.List;

@Repository
interface FriendOrFoeRepository {

    @Find
    @Transactional
    List<FriendOrFoeEntity> loadFriendsAndFoes(@By(_FriendOrFoeEntity.ID_PLAYER_ID) int playerId);

    @Save
    @Transactional
    void upsertPlayerRelationship(FriendOrFoeEntity entity);

    @Delete
    @Transactional
    void deletePlayerRelationship(@By(By.ID) FriendOrFoeEntity.Id id);

}
