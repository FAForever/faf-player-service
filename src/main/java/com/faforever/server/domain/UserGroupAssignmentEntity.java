package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_group_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
public class UserGroupAssignmentEntity extends AbstractEntity {

    @Column(name = "user_id")
    private int userId;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private UserGroupEntity userGroup;

}
