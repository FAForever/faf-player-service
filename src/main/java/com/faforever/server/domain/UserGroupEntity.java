package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Getter
@Setter
@Entity
@Table(name = "user_group")
public class UserGroupEntity extends AbstractEntity {

    @Column(name = "technical_name")
    String technicalName;

    @OneToMany
    @JoinTable(
            name = "group_permission_assignment",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<GroupPermissionEntity> groupPermissions = new HashSet<>();


}
