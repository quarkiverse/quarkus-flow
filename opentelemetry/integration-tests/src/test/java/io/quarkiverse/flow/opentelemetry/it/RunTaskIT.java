package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@EnabledOnOs(value = OS.LINUX)
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
