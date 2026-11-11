package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public record LeaderboardStats(
        int numberOfGames,
        Rating rating
) {
    public record Rating(
            double mean,
            double deviation
    ) {

        @JsonCreator
        public Rating(List<Double> values) {
            this(values.getFirst(), values.getLast());
        }

        @JsonValue
        public List<Double> asList() {
            return List.of(mean(), deviation());
        }
    }
}
