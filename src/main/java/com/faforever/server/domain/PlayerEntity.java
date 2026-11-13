package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "login")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
public class PlayerEntity extends AbstractEntity {

    @Column(name = "login")
    private String name;

    @Column(name = "ip")
    private String ip;

    @ManyToOne
    @JoinTable(
            name = "clan_membership",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "clan_id")
    )
    private @Nullable ClanEntity clan;

    @ManyToOne
    @JoinColumn(name = "avatar_id")
    private @Nullable AvatarEntity selectedAvatar;
}
