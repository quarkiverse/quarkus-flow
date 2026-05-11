package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class WaitTaskIT extends OTelBaseIT {

    private static final String WAIT_TASK1 = "do/0/waitTask1";
    private static final String SET_TASK = "do/1/setTask";
    private static final String WAIT_TASK2 = "do/2/waitTask2";

    @Override
    String workflowName() {
        return "otel-wait-task";
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(4);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(WAIT_TASK1), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SET_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(WAIT_TASK2), workflowParentSpan());
    }
}
