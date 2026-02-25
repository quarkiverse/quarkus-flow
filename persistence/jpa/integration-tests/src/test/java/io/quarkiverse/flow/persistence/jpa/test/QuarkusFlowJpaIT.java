package io.quarkiverse.flow.persistence.jpa.test;

import jakarta.inject.Inject;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.test.AbstractHandlerPersistenceTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowJpaIT extends AbstractHandlerPersistenceTest {
    @Inject
    PersistenceInstanceHandlers handlers;

    protected PersistenceInstanceHandlers getPersistenceHandlers() {
        return handlers;
    }
}
