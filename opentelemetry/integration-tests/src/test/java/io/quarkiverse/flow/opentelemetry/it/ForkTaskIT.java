package io.quarkiverse.flow.opentelemetry.it;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo;
import io.quarkiverse.flow.opentelemetry.it.util.IndexedSpanInfo.TaskSpanKey;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class ForkTaskIT extends OTelBaseIT {

    private static final String FORK_TASK = "do/0/forkTask/branch";
    private static final String FORK_BRANCH1_SET_TASK = "do/0/forkTask/branch/0/branch1";
    private static final String FORK_BRANCH2_DO_TASK = "do/0/forkTask/branch/1/branch2/do";
    private static final String FORK_BRANCH2_DO_SET1_ON_BRANCH2_TASK = "do/0/forkTask/branch/1/branch2/do/0/set1OnBranch2";
    private static final String FORK_BRANCH2_DO_SET2_ON_BRANCH2_TASK = "do/0/forkTask/branch/1/branch2/do/1/set2OnBranch2";
    private static final String SET_END_TASK = "do/1/setEndTask";

    @Override
    String workflowName() {
        return "otel-fork-task";
    }

    @Test
    void producedSpans() {
        IndexedSpanInfo indexedSpanInfo = executeAndGetSpans(7);

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FORK_TASK), workflowParentSpan());

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FORK_BRANCH1_SET_TASK), TaskSpanKey.from(FORK_TASK));

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FORK_BRANCH2_DO_TASK), TaskSpanKey.from(FORK_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FORK_BRANCH2_DO_SET1_ON_BRANCH2_TASK),
                TaskSpanKey.from(FORK_BRANCH2_DO_TASK));
        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(FORK_BRANCH2_DO_SET2_ON_BRANCH2_TASK),
                TaskSpanKey.from(FORK_BRANCH2_DO_TASK));

        assertThatHasParent(indexedSpanInfo, TaskSpanKey.from(SET_END_TASK), workflowParentSpan());
    }
}
