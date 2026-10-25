package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
abstract class TimeTrackedEntity extends AbstractEntity {

    @Column(name = "create_time")
    @CreationTimestamp(source = SourceType.DB)
    private Instant createTime;

    @Column(name = "update_time")
    @UpdateTimestamp(source = SourceType.DB)
    private Instant updateTime;

}
