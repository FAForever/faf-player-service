package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "group_permission")
public class GroupPermissionEntity extends AbstractEntity {

    @Column(name = "technical_name")
    String technicalName;


}
