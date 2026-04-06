package io.quarkiverse.flow.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.serverlessworkflow.impl.WorkflowStatus;

public class MVStoreWorkflowInstanceStoreTest {

    private static final ObjectMapper objectMapper;
    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(FlowInstance.class, new FlowInstanceSerializer());
        module.addDeserializer(FlowInstance.class, new FlowInstanceDeserializer());

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(module);
    }

    @TempDir
    Path tempDir;

    private MVStoreWorkflowInstanceStore store;
    private Path dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test-flow.mv.db");
        store = createStore(dbPath.toString());
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    private MVStoreWorkflowInstanceStore createStore(String path) {
        MVStoreWorkflowInstanceStore store = new MVStoreWorkflowInstanceStore(path, objectMapper);

        store.init();
        return store;
    }

    @Test
    void should_save_and_find_by_instance_id() {
        FlowInstance instance = createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z"));

        store.saveOrUpdate(instance);

        FlowInstance found = store.findByInstanceId("id1");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(found).isNotNull();
            softly.assertThat(found.getInstanceId()).isEqualTo("id1");
            softly.assertThat(found.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        });
    }

    @Test
    void should_handle_null_instance_save() {
        store.saveOrUpdate(null);
        assertThat(store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_ASC)).isEmpty();
    }

    @Test
    void should_find_by_status() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.COMPLETED,
                Instant.parse("2024-01-01T11:00:00Z"), Instant.parse("2024-01-01T11:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T12:00:00Z"), Instant.parse("2024-01-01T12:05:00Z")));

        List<FlowInstance> running = store.findByStatus(WorkflowStatus.RUNNING, 0, 10);
        List<FlowInstance> completed = store.findByStatus(WorkflowStatus.COMPLETED, 0, 10);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(running).hasSize(2);
            softly.assertThat(running).extracting(FlowInstance::getInstanceId).containsExactlyInAnyOrder("id1", "id3");
            softly.assertThat(completed).hasSize(1);
            softly.assertThat(completed.get(0).getInstanceId()).isEqualTo("id2");
        });
    }

    @Test
    void should_sort_by_start_time_ascending() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T12:00:00Z"), Instant.parse("2024-01-01T12:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T11:00:00Z"), Instant.parse("2024-01-01T11:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(3);
            softly.assertThat(sorted).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id2", "id3", "id1");
        });
    }

    @Test
    void should_paginate_correctly() {
        for (int i = 1; i <= 5; i++) {
            store.saveOrUpdate(createWorkflowInstance("id" + i, WorkflowStatus.RUNNING,
                    Instant.parse("2024-01-01T10:00:00Z").plusSeconds(i),
                    Instant.parse("2024-01-01T10:05:00Z").plusSeconds(i)));
        }

        List<FlowInstance> page0 = store.listAll(0, 2, WorkflowInstanceStore.Sort.START_TIME_ASC);
        List<FlowInstance> page1 = store.listAll(1, 2, WorkflowInstanceStore.Sort.START_TIME_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(page0).hasSize(2);
            softly.assertThat(page0).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id1", "id2");
            softly.assertThat(page1).hasSize(2);
            softly.assertThat(page1).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id3", "id4");
        });
    }

    @Test
    void should_return_empty_list_for_invalid_pagination() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        assertThat(store.listAll(-1, 10, WorkflowInstanceStore.Sort.START_TIME_ASC)).isEmpty();
        assertThat(store.listAll(0, 0, WorkflowInstanceStore.Sort.START_TIME_ASC)).isEmpty();
        assertThat(store.listAll(0, -1, WorkflowInstanceStore.Sort.START_TIME_ASC)).isEmpty();
    }

    @Test
    void should_update_existing_instance() {
        FlowInstance original = createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z"));
        store.saveOrUpdate(original);

        FlowInstance updated = createWorkflowInstance("id1", WorkflowStatus.COMPLETED,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:10:00Z"));
        store.saveOrUpdate(updated);

        FlowInstance found = store.findByInstanceId("id1");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(found.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
            softly.assertThat(store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_ASC)).hasSize(1);
        });
    }

    @Test
    void should_persist_data_across_store_restarts() {
        // Save some data
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.COMPLETED,
                Instant.parse("2024-01-01T11:00:00Z"), Instant.parse("2024-01-01T11:05:00Z")));

        // Close the store
        store.close();

        // Verify file exists
        assertThat(Files.exists(dbPath)).isTrue();

        // Reopen the store
        store = createStore(dbPath.toString());

        // Verify data was persisted
        List<FlowInstance> instances = store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_ASC);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(instances).hasSize(2);
            softly.assertThat(instances).extracting(FlowInstance::getInstanceId)
                    .containsExactlyInAnyOrder("id1", "id2");
        });
    }

    private FlowInstance createWorkflowInstance(String id, WorkflowStatus status, Instant startTime,
            Instant lastUpdateTime) {
        FlowInstance instance = new FlowInstance(
                id,
                "test-namespace",
                "test-workflow",
                "1.0",
                status,
                startTime,
                null);
        // Update lastUpdateTime if different from startTime
        if (lastUpdateTime != null && !lastUpdateTime.equals(startTime)) {
            instance.recordTaskEvent("test.event", null, lastUpdateTime);
        }
        return instance;
    }
}
