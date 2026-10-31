package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "leaderboard_rating")
public class LeaderboardRatingEntity extends TimeTrackedEntity {

    @Column(name = "login_id")
    private int playerId;

    @ManyToOne
    @JoinColumn(name = "leaderboard_id")
    private LeaderboardEntity leaderboard;

    @Column(name = "mean")
    private Double mean;

    @Column(name = "deviation")
    private Double deviation;

    @Column(name = "total_games")
    private int totalGames;

}
