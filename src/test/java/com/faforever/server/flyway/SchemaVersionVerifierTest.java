package com.faforever.server.flyway;

import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class SchemaVersionVerifierTest {

    @Inject
    SchemaVersionVerifier schemaVersionVerifier;

    @InjectMock
    private SchemaVersionRepository schemaVersionRepository;

    @Test
    @TestConfigProperty(key = "faf.minimum-database-version", value = "10")
    void testValidVersion() {
        when(schemaVersionRepository.findMaxVersion()).thenReturn(Optional.of("11"));

        assertDoesNotThrow(() -> schemaVersionVerifier.checkMinimumVersion());
    }

    @Test
    @TestConfigProperty(key = "faf.minimum-database-version", value = "10")
    void testInvalidVersion() {
        when(schemaVersionRepository.findMaxVersion()).thenReturn(Optional.of("9"));

        assertThrows(IllegalStateException.class, () -> schemaVersionVerifier.checkMinimumVersion());
    }

}
