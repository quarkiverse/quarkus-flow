package io.quarkiverse.flow.langchain4j.workflow;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public final class FlowPlannerSessionsAwait {

    private FlowPlannerSessionsAwait() {
    }

    public static void awaitNoSessions() {
        awaitNoSessions(5);
    }

    public static void awaitNoSessions(int timeoutSeconds) {
        FlowPlannerSessions s = FlowPlannerSessions.getInstance();
        await()
                .atMost(timeoutSeconds, SECONDS)
                .pollInterval(10, MILLISECONDS)
                .untilAsserted(() -> {
                    int n = s.activeSessionCount();
                    assertThat(n)
                            .withFailMessage("Leaked FlowPlannerSessions (%s): %s", n, s.activeSessionIds())
                            .isZero();
                });
    }
}
