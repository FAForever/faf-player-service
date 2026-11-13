package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "friends_and_foes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuppressWarnings("NullAway.Init")
public class FriendOrFoeEntity {

    @EmbeddedId
    private Id id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    public enum Status {
        FRIEND, FOE
    }

    @Embeddable
    public record Id(
            @Column(name = "user_id") int playerId,
            @Column(name = "subject_id") int subjectId
    ) {}

}
