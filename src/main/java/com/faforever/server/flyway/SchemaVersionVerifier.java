package com.faforever.server.flyway;

import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class SchemaVersionVerifier {

    private final SchemaVersionRepository schemaVersionRepository;
    private final int minimumVersion;

    public SchemaVersionVerifier(SchemaVersionRepository schemaVersionRepository,
                                 @ConfigProperty(name = "faf.minimum-database-version") int minimumVersion) {
        this.schemaVersionRepository = schemaVersionRepository;
        this.minimumVersion = minimumVersion;
    }

    @Startup
    void checkMinimumVersion() {
        int actualVersion = schemaVersionRepository.findMaxVersion()
                                                   .map(Integer::parseInt)
                                                   .orElseThrow(() -> new IllegalStateException(
                                                           "No database version is available"));

        if (actualVersion < minimumVersion) {
            throw new IllegalStateException(
                    "Database version is '%d' but this software requires at least '%d'".formatted(actualVersion,
                                                                                                  minimumVersion));
        }
    }
}
