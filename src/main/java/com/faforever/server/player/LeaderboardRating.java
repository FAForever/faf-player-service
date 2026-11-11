package com.faforever.server.player;

record LeaderboardRating(
        String leaderboard,
        int totalGames,
        double mean,
        double deviation
) {}
