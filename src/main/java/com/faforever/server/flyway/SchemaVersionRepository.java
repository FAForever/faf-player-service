package com.faforever.server.flyway;

import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import java.util.Optional;

@Repository
interface SchemaVersionRepository {

    @Query("select s.version from SchemaVersion s where s.version is not null and s.success order by s.installedRank desc limit 1")
    Optional<String> findMaxVersion();
}
