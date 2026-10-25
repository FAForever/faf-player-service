package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "leaderboard")
public class LeaderboardEntity extends AbstractEntity {

    @Column(name = "technical_name")
    private String technicalName;

}
