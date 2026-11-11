package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain.LeaderboardRatingEntity;
import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.domain._FriendOrFoeEntity;
import com.faforever.server.domain._LeaderboardRatingEntity;
import jakarta.data.constraint.AtLeast;
import jakarta.data.constraint.In;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Is;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
interface PlayerStateRepository {

    @Find
    Optional<PlayerEntity> loadPlayer(@By(By.ID) int id);

    @Find
    List<FriendOrFoeEntity> loadFriendsAndFoes(@By(_FriendOrFoeEntity.ID_PLAYER_ID) int playerId);

    @Query("select case when count(u) > 0 then true else false end from UserGroupAssignment u where u.userId = :playerId and u.userGroup.technicalName = 'faf_moderators_global'")
    boolean isPlayerModerator(int playerId);

    @Find
    List<LeaderboardRatingEntity> loadUpdatedLeaderboardRatings(
            @By(_LeaderboardRatingEntity.PLAYER_ID) @Is(In.class) Collection<Integer> playerIds,
            @By(_LeaderboardRatingEntity.UPDATE_TIME) @Is(AtLeast.class) Instant timeCutoff);

    @Find
    List<LeaderboardRatingEntity> loadPlayerRatings(@By(_LeaderboardRatingEntity.PLAYER_ID) int playerId);

    @Find
    Optional<AvatarEntity> findAvatar(@By(By.ID) int avatarId);

    @Query("update PlayerEntity p set p.ip = :ipAddress where p.id = :playerId")
    void updatePlayerIpAddress(@Param("playerId") int playerId, @Param("ipAddress") String ipAddress);
}
