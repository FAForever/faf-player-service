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
@Table(name = "user_group_assignment")
public class UserGroupAssignment extends AbstractEntity {

    @Column(name = "user_id")
    private int userId;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private UserGroupEntity userGroup;

}
