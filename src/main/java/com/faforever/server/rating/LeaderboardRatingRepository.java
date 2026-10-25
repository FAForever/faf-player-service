package com.faforever.server.rating;

import com.faforever.server.domain.LeaderboardRatingEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class LeaderboardRatingRepository implements PanacheRepositoryBase<LeaderboardRatingEntity, Integer> {

    @Transactional
    public Map<Integer, Set<LeaderboardRating>> loadUpdatedLeaderboardRatings(Collection<Integer> playerIds,
                                                                              Instant timeCutoff) {
        return find("player.id in (?1) and updateTime > ?2", playerIds, timeCutoff).stream()
                                                                                                            .collect(Collectors.groupingBy(
                                                                                           leaderboardRatingEntity -> leaderboardRatingEntity.getPlayer()
                                                                                                                                             .getId(),
                                                                                           Collectors.mapping(
                                                                                                   leaderboardRatingEntity -> {
                                                                                                       Leaderboard leaderboard = new Leaderboard(
                                                                                                               leaderboardRatingEntity.getLeaderboard()
                                                                                                                                      .getTechnicalName());
                                                                                                       return new LeaderboardRating(
                                                                                                               leaderboard,
                                                                                                               leaderboardRatingEntity.getTotalGames(),
                                                                                                               leaderboardRatingEntity.getMean(),
                                                                                                               leaderboardRatingEntity.getDeviation());
                                                                                                   },
                                                                                                   Collectors.toSet())));
    }

}
