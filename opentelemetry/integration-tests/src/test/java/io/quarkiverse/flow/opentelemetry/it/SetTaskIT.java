package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class SetTaskIT extends OTelBaseIT {

    private static final String SET_TASK = "do/0/setTask";

    @Override
    String workflowName() {
        return "otel-set-task";
    }

    @Override
    String workVersion() {
        return "1.0.0";
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(2);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SET_TASK), workflowParentSpan());
    }
}
