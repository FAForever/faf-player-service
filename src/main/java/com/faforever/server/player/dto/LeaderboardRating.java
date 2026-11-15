package com.faforever.server.player.dto;

public record LeaderboardRating(
        String leaderboard,
        int totalGames,
        double mean,
        double deviation
) {}
