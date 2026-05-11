package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class DoTaskIT extends OTelBaseIT {

    private static final String DO_TASK = "do/0/doTask/do";
    private static final String DO_TASK_SET1 = "do/0/doTask/do/0/set1";
    private static final String DO_TASK_SET2 = "do/0/doTask/do/1/set2";
    private static final String DO_TASK_SET3 = "do/0/doTask/do/2/set3";

    @Override
    String workflowName() {
        return "otel-do-task";
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(5);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(DO_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(DO_TASK_SET1), TaskSpanKey.from(DO_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(DO_TASK_SET2), TaskSpanKey.from(DO_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(DO_TASK_SET3), TaskSpanKey.from(DO_TASK));
    }
}
