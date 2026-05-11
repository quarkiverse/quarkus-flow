package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class TryTaskIT extends OTelBaseIT {

    public static final String TRY_TASK = "do/0/tryTask/try";
    public static final String FAILING_TASK = "do/0/tryTask/try/0/failingTask";
    public static final String EXECUTE_AFTER_FAILING_TASK = "do/0/tryTask/catch/do/0/executeAfterFailingTask";

    @Override
    String workflowName() {
        return "otel-try-task";
    }

    @Override
    int statusCode() {
        return 500;
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(10);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(TRY_TASK), workflowParentSpan());

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FAILING_TASK, 1, 0, false), TaskSpanKey.from(TRY_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(EXECUTE_AFTER_FAILING_TASK, 1, 0, false),
                TaskSpanKey.from(TRY_TASK));

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FAILING_TASK, 2, 1, true), TaskSpanKey.from(TRY_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(EXECUTE_AFTER_FAILING_TASK, 2, 1, true),
                TaskSpanKey.from(TRY_TASK));

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FAILING_TASK, 3, 2, true), TaskSpanKey.from(TRY_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(EXECUTE_AFTER_FAILING_TASK, 3, 2, true),
                TaskSpanKey.from(TRY_TASK));

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FAILING_TASK, 4, 3, true), TaskSpanKey.from(TRY_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(EXECUTE_AFTER_FAILING_TASK, 4, 3, true),
                TaskSpanKey.from(TRY_TASK));
    }
}
