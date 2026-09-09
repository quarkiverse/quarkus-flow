package io.quarkiverse.flow.persistence.redis.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowPosition;
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
        SetCommands<String, String> setCommands = redis.set(String.class, String.class);
        String instanceId = workflowInstance.id();
        String indexKey = "idx:" + instanceId;

        // the instance hash itself is never indexed, in either mode
        handlers.writer().started(workflowContext).join();
        assertThat(keyCommands.keys(indexKey)).as("started() must not create an index Set").isEmpty();

        // a task write is what populates the index, and only under 'indexed'
        String pointer = "/do/0/useExpression";
        WorkflowPosition position = mock(WorkflowPosition.class);
        when(position.jsonPointer()).thenReturn(pointer);
        TaskContext taskContext = mock(TaskContext.class);
        when(taskContext.position()).thenReturn(position);
        when(taskContext.retryAttempt()).thenReturn(1);
        handlers.writer().taskRetried(workflowContext, taskContext).join();

        String taskKey = instanceId + ":" + pointer;
        if (indexed()) {
            assertThat(setCommands.smembers(indexKey))
                    .as("indexed mode tracks task keys in a per-instance Set")
                    .containsExactly(taskKey);
        } else {
            assertThat(keyCommands.keys(indexKey)).as("scan mode keeps no index Set").isEmpty();
        }

        // removal cleans everything up in both modes
        handlers.writer().completed(workflowContext).join();
        assertThat(keyCommands.keys(indexKey)).as("index Set removed").isEmpty();
        assertThat(keyCommands.keys(instanceId + ":*")).as("task keys removed").isEmpty();
        assertThat(keyCommands.keys("*" + instanceId)).as("instance hash removed").isEmpty();
    }
}
