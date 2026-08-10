package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@DisabledOnOs(OS.WINDOWS)
class RunTaskIT extends OTelBaseIT {

    private static final String RUN_TASK = "do/0/runTask";

    @Override
    String workflowName() {
        return "otel-run-task";
    }

    @Override
    String workVersion() {
        return "1.0.0";
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(2);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(RUN_TASK), workflowParentSpan());
    }
}
