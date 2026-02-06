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
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.test.AbstractPersistenceTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowMVStoreTest extends AbstractPersistenceTest {

    @Inject
    PersistenceInstanceStore store;

    @Inject
    MVStoreConfig config;

    @Override
    protected PersistenceInstanceStore persistenceStore() {
        return store;
    }

    @AfterEach
    void destroy() throws IOException {
        Files.delete(Path.of(config.dbPath()));
    }
}
