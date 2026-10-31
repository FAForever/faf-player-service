package com.faforever.server.rating;

import com.faforever.server.domain.LeaderboardRatingEntity;

public record LeaderboardRating(
        String leaderboard,
        int totalGames,
        double mean,
        double deviation
) {

    public static LeaderboardRating from(LeaderboardRatingEntity leaderboardRatingEntity) {
        return new LeaderboardRating(leaderboardRatingEntity.getLeaderboard().getTechnicalName(),
                                     leaderboardRatingEntity.getTotalGames(), leaderboardRatingEntity.getMean(),
                                     leaderboardRatingEntity.getDeviation());
    }

}
