package com.faforever.server.flyway;

import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
class SchemaVersionVerifier {

    private final SchemaVersionRepository schemaVersionRepository;
    private final int minimumVersion;

    SchemaVersionVerifier(SchemaVersionRepository schemaVersionRepository,
                          @ConfigProperty(name = "faf.minimum-database-version") int minimumVersion) {
        this.schemaVersionRepository = schemaVersionRepository;
        this.minimumVersion = minimumVersion;
    }

    @Startup
    void checkMinimumVersion() {
        String maxVersion = schemaVersionRepository.findMaxVersion()
                                                   .orElseThrow(() -> new IllegalStateException(
                                                           "No database version is available"));

        int actualVersion = Integer.parseInt(maxVersion);

        if (actualVersion < minimumVersion) {
            throw new IllegalStateException(
                    "Database version is '%d' but this software requires at least '%d'".formatted(actualVersion,
                                                                                                  minimumVersion));
        }
    }
}
