package io.quarkiverse.flow.persistence.mvstore.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.persistence.mvstore.MVStoreConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.test.AbstractHandlerPersistenceTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowMVStoreTest extends AbstractHandlerPersistenceTest {

    @Inject
    PersistenceInstanceHandlers handlers;

    @Inject
    MVStoreConfig config;

    @AfterEach
    void destroy() throws IOException {
        Files.delete(Path.of(config.dbPath()));
    }

    @Override
    protected PersistenceInstanceHandlers getPersistenceHandlers() {
        return handlers;
    }
}
