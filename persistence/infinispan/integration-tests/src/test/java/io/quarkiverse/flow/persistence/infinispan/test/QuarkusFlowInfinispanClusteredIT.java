package io.quarkiverse.flow.persistence.infinispan.test;

import jakarta.inject.Inject;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.test.AbstractHandlerPersistenceTest;

@QuarkusTest
@QuarkusTestResource(value = InfinispanRespClusteredResource.class, restrictToAnnotatedClass = true)
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowInfinispanClusteredIT extends AbstractHandlerPersistenceTest {

    @Inject
    PersistenceInstanceHandlers handlers;

    @Override
    protected PersistenceInstanceHandlers getPersistenceHandlers() {
        return handlers;
    }
}
