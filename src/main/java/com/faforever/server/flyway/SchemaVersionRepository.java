package com.faforever.server.flyway;

import com.faforever.server.domain.SchemaVersion;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class SchemaVersionRepository implements PanacheRepositoryBase<SchemaVersion, Integer> {

    Optional<String> findMaxVersion() {
        return find(
                "select s.version from SchemaVersion s where s.version is not null and s.success order by s.installedRank desc").project(
                String.class).firstResultOptional();
    }
}
