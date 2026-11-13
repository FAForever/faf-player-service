package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "avatars")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
public class AssignedAvatarEntity extends AbstractEntity {

    @Column(name = "idUser")
    private int playerId;

    @ManyToOne
    @JoinColumn(name = "idAvatar")
    private AvatarEntity avatar;

    @Column(name = "selected")
    private boolean selected;

    @Column(name = "expires_at")
    private OffsetDateTime expirationDate;


}
