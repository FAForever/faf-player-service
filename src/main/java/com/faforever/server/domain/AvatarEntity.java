package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "avatars_list")
public class AvatarEntity extends AbstractEntity {

    @Column(name = "tooltip")
    private String description;

    @Column(name = "url")
    private String url;

}
