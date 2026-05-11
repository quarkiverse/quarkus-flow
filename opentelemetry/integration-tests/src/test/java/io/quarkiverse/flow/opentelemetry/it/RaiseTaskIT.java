package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class RaiseTaskIT extends OTelBaseIT {

    private static final String SET_BEFORE_RAISE = "do/0/setBeforeRaise";
    private static final String RAISE_TASK = "do/1/raiseTask";

    @Override
    String workflowName() {
        return "otel-raise-task";
    }

    @Override
    int statusCode() {
        return 500;
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(3);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SET_BEFORE_RAISE), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(RAISE_TASK), workflowParentSpan());
    }

}
