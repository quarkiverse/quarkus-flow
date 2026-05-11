package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class SwitchTaskIT extends OTelBaseIT {

    private static final String SWITCH_TASK = "do/0/switchTask";
    private static final String PROCESS_CASE1_TASK = "do/1/processCase1Task/do";
    private static final String PROCESS_CASE1_SET_TASK = "do/1/processCase1Task/do/0/processCase1SetTask";
    private static final String PROCESS_CASE2_TASK = "do/2/processCase2Task/do";
    private static final String PROCESS_CASE2_SET_TASK = "do/2/processCase2Task/do/0/processCase2SetTask";
    private static final String HANDLE_UNKNOWN_CASE_TASK = "do/3/handleUnknownCase/do";
    private static final String HANDLE_UNKNOWN_CASE_SET1_TASK = "do/3/handleUnknownCase/do/0/handleUnknownCaseSet1";
    private static final String HANDLE_UNKNOWN_CASE_SET2_TASK = "do/3/handleUnknownCase/do/1/handleUnknownCaseSet2";

    @Override
    String workflowName() {
        return "otel-switch-task";
    }

    @Test
    void producedSpansCase1() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(4, "{\"selectedCase\" : \"case1\"}");
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SWITCH_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(PROCESS_CASE1_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(PROCESS_CASE1_SET_TASK), TaskSpanKey.from(PROCESS_CASE1_TASK));
    }

    @Test
    void producedSpansCase2() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(4, "{\"selectedCase\" : \"case2\"}");
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SWITCH_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(PROCESS_CASE2_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(PROCESS_CASE2_SET_TASK), TaskSpanKey.from(PROCESS_CASE2_TASK));
    }

    @Test
    void producedSpansUnknownCase() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(5);
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SWITCH_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(HANDLE_UNKNOWN_CASE_TASK), workflowParentSpan());
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(HANDLE_UNKNOWN_CASE_SET1_TASK),
                TaskSpanKey.from(HANDLE_UNKNOWN_CASE_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(HANDLE_UNKNOWN_CASE_SET2_TASK),
                TaskSpanKey.from(HANDLE_UNKNOWN_CASE_TASK));
    }
}
