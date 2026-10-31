package com.faforever.server.rating;

import com.faforever.server.domain.LeaderboardRatingEntity;
import com.faforever.server.domain._LeaderboardRatingEntity;
import jakarta.data.constraint.AtLeast;
import jakarta.data.constraint.In;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Is;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaderboardRatingRepository {

    @Find
    @Transactional
    List<LeaderboardRatingEntity> loadUpdatedLeaderboardRatings(
            @By(_LeaderboardRatingEntity.PLAYER_ID) @Is(In.class) Collection<Integer> playerIds,
            @By(_LeaderboardRatingEntity.UPDATE_TIME) @Is(AtLeast.class) Instant timeCutoff);

    @Find
    @Transactional
    List<LeaderboardRatingEntity> loadPlayerRatings(@By(_LeaderboardRatingEntity.PLAYER_ID) int playerId);

}
