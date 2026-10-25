package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "clan")
public class ClanEntity extends AbstractEntity {

    @Column(name = "tag")
    private String tag;

}
