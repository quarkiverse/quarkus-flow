package io.quarkiverse.flow.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.WorkflowStatus;

public class InMemoryFlowInstanceStoreTest {

    private InMemoryWorkflowInstanceStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowInstanceStore();
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
    void should_sort_by_start_time_descending() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T12:00:00Z"), Instant.parse("2024-01-01T12:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T11:00:00Z"), Instant.parse("2024-01-01T11:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_DESC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(3);
            softly.assertThat(sorted).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id1", "id3", "id2");
        });
    }

    @Test
    void should_sort_by_last_update_ascending() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T12:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T11:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.LAST_UPDATE_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(3);
            softly.assertThat(sorted).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id2", "id3", "id1");
        });
    }

    @Test
    void should_sort_by_last_update_descending() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T12:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T11:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.LAST_UPDATE_DESC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(3);
            softly.assertThat(sorted).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id1", "id3", "id2");
        });
    }

    @Test
    void should_sort_by_status_ascending() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.COMPLETED,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.FAULTED,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.STATUS_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(3);
            softly.assertThat(sorted).extracting(FlowInstance::getStatus)
                    .containsExactly(WorkflowStatus.COMPLETED, WorkflowStatus.FAULTED, WorkflowStatus.RUNNING);
        });
    }

    @Test
    void should_sort_by_status_descending() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.COMPLETED,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id3", WorkflowStatus.FAULTED,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.STATUS_DESC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(3);
            softly.assertThat(sorted).extracting(FlowInstance::getStatus)
                    .containsExactly(WorkflowStatus.RUNNING, WorkflowStatus.FAULTED, WorkflowStatus.COMPLETED);
        });
    }

    @Test
    void should_paginate_with_page_zero() {
        for (int i = 1; i <= 5; i++) {
            store.saveOrUpdate(createWorkflowInstance("id" + i, WorkflowStatus.RUNNING,
                    Instant.parse("2024-01-01T10:00:00Z").plusSeconds(i),
                    Instant.parse("2024-01-01T10:05:00Z").plusSeconds(i)));
        }

        List<FlowInstance> page0 = store.listAll(0, 2, WorkflowInstanceStore.Sort.START_TIME_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(page0).hasSize(2);
            softly.assertThat(page0).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id1", "id2");
        });
    }

    @Test
    void should_paginate_with_page_one() {
        for (int i = 1; i <= 5; i++) {
            store.saveOrUpdate(createWorkflowInstance("id" + i, WorkflowStatus.RUNNING,
                    Instant.parse("2024-01-01T10:00:00Z").plusSeconds(i),
                    Instant.parse("2024-01-01T10:05:00Z").plusSeconds(i)));
        }

        List<FlowInstance> page1 = store.listAll(1, 2, WorkflowInstanceStore.Sort.START_TIME_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(page1).hasSize(2);
            softly.assertThat(page1).extracting(FlowInstance::getInstanceId)
                    .containsExactly("id3", "id4");
        });
    }

    @Test
    void should_return_empty_list_for_negative_page() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> result = store.listAll(-1, 10, WorkflowInstanceStore.Sort.START_TIME_ASC);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_for_zero_size() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> result = store.listAll(0, 0, WorkflowInstanceStore.Sort.START_TIME_ASC);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_for_negative_size() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> result = store.listAll(0, -1, WorkflowInstanceStore.Sort.START_TIME_ASC);

        assertThat(result).isEmpty();
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
            softly.assertThat(found.getLastUpdateTime()).isEqualTo(Instant.parse("2024-01-01T10:10:00Z"));
            softly.assertThat(store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_ASC)).hasSize(1);
        });
    }

    @Test
    void should_handle_null_timestamps_in_sorting() {
        store.saveOrUpdate(createWorkflowInstance("id1", WorkflowStatus.RUNNING, null, null));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.START_TIME_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(2);
            // Null timestamps should be treated as MIN and come first
            softly.assertThat(sorted.get(0).getInstanceId()).isEqualTo("id1");
            softly.assertThat(sorted.get(1).getInstanceId()).isEqualTo("id2");
        });
    }

    @Test
    void should_handle_null_status_in_sorting() {
        store.saveOrUpdate(createWorkflowInstance("id1", null,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));
        store.saveOrUpdate(createWorkflowInstance("id2", WorkflowStatus.RUNNING,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T10:05:00Z")));

        List<FlowInstance> sorted = store.listAll(0, 10, WorkflowInstanceStore.Sort.STATUS_ASC);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sorted).hasSize(2);
            // Null status should be treated as empty string and come first
            softly.assertThat(sorted.get(0).getInstanceId()).isEqualTo("id1");
            softly.assertThat(sorted.get(1).getInstanceId()).isEqualTo("id2");
        });
    }

    private FlowInstance createWorkflowInstance(String id, WorkflowStatus status, Instant startTime, Instant lastUpdateTime) {
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
