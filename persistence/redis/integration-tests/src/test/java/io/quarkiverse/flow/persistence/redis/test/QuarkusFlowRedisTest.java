package io.quarkiverse.flow.persistence.redis.test;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.test.AbstractPersistenceTest;

@QuarkusTest
public class QuarkusFlowRedisTest extends AbstractPersistenceTest {
    @Inject
    PersistenceInstanceStore store;

    @Override
    protected PersistenceInstanceStore persistenceStore() {
        return store;
    }
}
