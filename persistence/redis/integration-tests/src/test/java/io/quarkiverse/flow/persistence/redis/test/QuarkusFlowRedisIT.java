package io.quarkiverse.flow.persistence.redis.test;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.persistence.redis.RedisInstanceStore;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.test.AbstractPersistenceTest;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class QuarkusFlowRedisIT extends AbstractPersistenceTest {
    @Inject
    PersistenceInstanceStore store;

    @Override
    protected PersistenceInstanceStore persistenceStore() {
        return store;
    }

    @Test
    void store_should_be_redis() {
        Assertions.assertTrue(store instanceof RedisInstanceStore);
        ;
    }
}
