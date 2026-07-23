package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.opentelemetry.it.util.Utils.deleteSpans;
import static io.quarkiverse.flow.opentelemetry.it.util.Utils.executeWorkflow;
import static io.quarkiverse.flow.opentelemetry.it.util.Utils.getWorkflowSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.WorkflowSpanKey;
import io.quarkiverse.flow.opentelemetry.it.util.SpanInfo;

abstract class OTelBaseIT {

    abstract String workflowName();

    @BeforeEach
    void setup() {
        deleteSpans();
    }

    int statusCode() {
        return 200;
    }

    String workVersion() {
        return "1.0.0";
    }

    WorkflowSpanKey workflowParentSpan() {
        return WorkflowSpanKey.from(workflowName(), workVersion());
    }

    IndexedSpanInfo executeAndGetSpans(int expectedSpans) {
        return executeAndGetSpans(expectedSpans, "{}");
    }

    IndexedSpanInfo executeAndGetSpans(int expectedSpans, String input) {
        executeWorkflow("/otel-workflows/" + workflowName(), input, 5, 60, statusCode());
        List<SpanInfo> spanList = getWorkflowSpansAtMost(workflowName(), workVersion(), expectedSpans, 60);
        IndexedSpanInfo indexedSpanInfo = IndexedSpanInfo.from(spanList);

        assertThat(indexedSpanInfo.getIndexedWorkflowSpans()).hasSize(1);
        assertThat(indexedSpanInfo.getIndexedTaskSpans()).hasSize(expectedSpans - 1);
        return indexedSpanInfo;
    }

    static List<SpanInfo> getWorkflowSpansAtMost(String workflowName, String workflowVersion, int size, int timeoutInSeconds) {
        AtomicReference<List<SpanInfo>> spans = new AtomicReference<>();
        await().atMost(timeoutInSeconds, TimeUnit.SECONDS)
                .until(() -> {
                    spans.set(getWorkflowSpans(workflowName, workflowVersion));
                    return spans.get().size() == size;
                });
        return spans.get();
    }

    static void assertThatHasParent(IndexedSpanInfo indexedSpanInfo, IndexedSpanInfo.TaskSpanKey taskSpanKey,
            IndexedSpanInfo.TaskSpanKey expectedParentKey) {
        SpanInfo taskSpanInfo = indexedSpanInfo.getSpanInfo(taskSpanKey);
        assertThat(taskSpanInfo)
                .withFailMessage("Task span %s is not present", taskSpanKey)
                .isNotNull();

        SpanInfo expectedParentSpanInfo = indexedSpanInfo.getSpanInfo(expectedParentKey);
        assertThat(expectedParentSpanInfo)
                .withFailMessage("Parent task span %s is not present", expectedParentKey)
                .isNotNull();

        assertThat(taskSpanInfo.getTraceId())
                .withFailMessage("Task span %s and expected parent task span %s, belong to different traces", taskSpanKey,
                        expectedParentKey)
                .isEqualTo(expectedParentSpanInfo.getTraceId());

        assertThat(taskSpanInfo.getParentSpanId())
                .withFailMessage(
                        "Task span %s is not child of the expected parent task span %s",
                        taskSpanKey, expectedParentKey)
                .isEqualTo(expectedParentSpanInfo.getSpanId());
    }

    static void assertThatHasParent(IndexedSpanInfo indexedSpanInfo, IndexedSpanInfo.TaskSpanKey taskSpanKey,
            IndexedSpanInfo.WorkflowSpanKey expectedParentKey) {
        SpanInfo taskSpanInfo = indexedSpanInfo.getSpanInfo(taskSpanKey);
        assertThat(taskSpanInfo)
                .withFailMessage("Task span %s is not present", taskSpanKey)
                .isNotNull();

        SpanInfo expectedParentSpanInfo = indexedSpanInfo.getSpanInfo(expectedParentKey);
        assertThat(expectedParentSpanInfo)
                .withFailMessage("Expected parent workflow span %s is not present", expectedParentKey)
                .isNotNull();

        assertThat(taskSpanInfo.getTraceId())
                .withFailMessage("Task span %s and expected parent workflow span %s, belong to different traces", taskSpanKey,
                        expectedParentKey)
                .isEqualTo(expectedParentSpanInfo.getTraceId());

        assertThat(taskSpanInfo.getParentSpanId())
                .withFailMessage(
                        "Task span %s is not child of the expected parent workflow span %s",
                        taskSpanKey, expectedParentKey)
                .isEqualTo(expectedParentSpanInfo.getSpanId());
    }
}
