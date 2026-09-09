package io.quarkiverse.flow.persistence.redis.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.test.AbstractHandlerPersistenceTest;

public abstract class AbstractRedisKeyTrackingIT extends AbstractHandlerPersistenceTest {

    @Inject
    PersistenceInstanceHandlers handlers;

    @Inject
    RedisDataSource redis;

    @Override
    protected PersistenceInstanceHandlers getPersistenceHandlers() {
        return handlers;
    }

    protected abstract boolean indexed();

    @Test
    void key_tracking_mode_controls_the_instance_index() {
        KeyCommands<String> keyCommands = redis.key(String.class);
        String key = "idx:" + workflowInstance.id();

        handlers.writer().started(workflowContext).join();

        List<String> indexKeys = keyCommands.keys(key);
        if (indexed()) {
            assertThat(indexKeys).as("per-instance index Set should be maintained").containsExactly(key);
        } else {
            assertThat(indexKeys).as("no per-instance index Set should be created").isEmpty();
        }

        // removal cleans everything up in both modes
        handlers.writer().completed(workflowContext).join();
        assertThat(keyCommands.keys(key)).isEmpty();
        assertThat(keyCommands.keys("*" + workflowInstance.id())).isEmpty();
    }
}
