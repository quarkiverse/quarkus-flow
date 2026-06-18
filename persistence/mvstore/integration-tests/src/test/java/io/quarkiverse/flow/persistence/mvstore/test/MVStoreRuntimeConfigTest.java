package io.quarkiverse.flow.persistence.mvstore.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.persistence.mvstore.MVStoreConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(MVStoreRuntimeConfigTest.RuntimeOverrideProfile.class)
@DisabledOnOs(OS.WINDOWS)
@DisplayName("MVStore Runtime Configuration Override Test")
public class MVStoreRuntimeConfigTest {

    private static final String RUNTIME_DB_PATH = "target/runtime-override-test.db";

    @Inject
    MVStoreConfig config;

    @AfterEach
    void cleanup() throws IOException {
        Path dbPath = Path.of(RUNTIME_DB_PATH);
        Files.deleteIfExists(dbPath);
        // Also delete the .mv file if it exists
        Files.deleteIfExists(Path.of(RUNTIME_DB_PATH + ".mv"));
    }

    @Test
    @DisplayName("test_db_path_can_be_overridden_at_runtime")
    void test_db_path_can_be_overridden_at_runtime() {
        // Verify that the runtime override from the test profile is applied
        assertThat(config.dbPath())
                .as("db-path should be overridable at runtime")
                .isEqualTo(RUNTIME_DB_PATH);
    }

    /**
     * Test profile that overrides the db-path at runtime.
     * This simulates what happens when a user sets QUARKUS_FLOW_PERSISTENCE_MVSTORE_DB_PATH
     * environment variable or uses -Dquarkus.flow.persistence.mvstore.db-path=... system property.
     */
    public static class RuntimeOverrideProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flow.persistence.mvstore.db-path", RUNTIME_DB_PATH);
        }
    }
}
