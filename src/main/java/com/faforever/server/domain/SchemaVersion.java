package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "flyway_schema_history")
@Setter
@Getter
public class SchemaVersion {

    @Id
    @Column(name = "installed_rank")
    private Integer installedRank;

    @Column(name = "version")
    private String version;
}
