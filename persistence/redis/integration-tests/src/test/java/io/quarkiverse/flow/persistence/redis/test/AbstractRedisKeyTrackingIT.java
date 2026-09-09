package io.quarkiverse.flow.persistence.redis.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
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
        String markerKey = indexKey + ":sync";

        // creating the instance writes the completeness marker (indexed only), never the Set
        handlers.writer().started(workflowContext).join();
        assertThat(keyCommands.keys(indexKey)).as("started() must not create the index Set").isEmpty();
        if (indexed()) {
            assertThat(keyCommands.keys(markerKey)).as("indexed mode marks the index complete at creation")
                    .containsExactly(markerKey);
        } else {
            assertThat(keyCommands.keys(markerKey)).as("scan mode writes no marker").isEmpty();
        }

        // a task write is what populates the Set, and only under 'indexed'
        String pointer = "/do/0/useExpression";
        handlers.writer().taskRetried(workflowContext, mockRetryTask(pointer, 1)).join();

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
        assertThat(keyCommands.keys(markerKey)).as("marker removed").isEmpty();
        assertThat(keyCommands.keys(instanceId + ":*")).as("task keys removed").isEmpty();
        assertThat(keyCommands.keys("*" + instanceId)).as("instance hash removed").isEmpty();
    }

    @Test
    void an_instance_without_the_complete_marker_is_still_fully_resolved_by_scan() {
        KeyCommands<String> keyCommands = redis.key(String.class);
        HashCommands<String, String, byte[]> hashCommands = redis.hash(String.class, String.class, byte[].class);
        SetCommands<String, String> setCommands = redis.set(String.class, String.class);
        String instanceId = workflowInstance.id();

        // the instance hash exists, but no completeness marker: it predates 'indexed' mode
        handlers.writer().started(workflowContext).join();
        keyCommands.del("idx:" + instanceId + ":sync");

        // a task hash written before the switch (never added to any Set)...
        String preSwitchTask = instanceId + ":/do/0/preSwitch";
        hashCommands.hset(preSwitchTask, "status", new byte[] { 1 });
        // ...and a partial index as if a post-switch task write had happened
        setCommands.sadd("idx:" + instanceId, instanceId + ":/do/1/postSwitch");

        handlers.writer().completed(workflowContext).join();

        assertThat(keyCommands.keys(instanceId + ":*")).as("the pre-switch task hash must be scanned and removed")
                .isEmpty();
        assertThat(keyCommands.keys("idx:" + instanceId + "*")).as("the partial index is removed too").isEmpty();
        assertThat(keyCommands.keys("*" + instanceId)).as("instance hash removed").isEmpty();
    }

    private TaskContext mockRetryTask(String pointer, int retryAttempt) {
        WorkflowPosition position = mock(WorkflowPosition.class);
        when(position.jsonPointer()).thenReturn(pointer);
        TaskContext taskContext = mock(TaskContext.class);
        when(taskContext.position()).thenReturn(position);
        when(taskContext.retryAttempt()).thenReturn(retryAttempt);
        return taskContext;
    }
}
