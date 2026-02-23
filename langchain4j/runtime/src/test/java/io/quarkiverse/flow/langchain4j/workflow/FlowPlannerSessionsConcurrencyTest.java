package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FlowPlannerSessionsConcurrencyTest {

    @Inject
    WorkflowRegistry registry;

    @AfterEach
    void noLeaks() {
        FlowPlannerSessionsAwait.awaitNoSessions(5);
    }

    @Test
    void parallel_invocations_close_sessions() throws Exception {

        var a = AgenticServices.agentAction(scope -> {
            String input = scope.readState("input", "");
            if (input.startsWith("boom"))
                throw new RuntimeException("boom in A");
            scope.writeState("calledA", true);
        });
        var b = AgenticServices.agentAction(scope -> scope.writeState("calledB", true));
        var c = AgenticServices.agentAction(scope -> scope.writeState("calledC", true));

        FlowParallelAgentService<TestParallelAgent> service = (FlowParallelAgentService<TestParallelAgent>) FlowParallelAgentService
                .builder(TestParallelAgent.class, registry).subAgents(a, b, c);

        TestParallelAgent agent = service.build();

        int tasks = 300;
        int threads = 24;
        int everyNthFails = 17;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(tasks);
            CountDownLatch start = new CountDownLatch(1);

            LongAdder ok = new LongAdder();
            LongAdder failed = new LongAdder();
            LongAdder nanos = new LongAdder();

            List<Future<Void>> futures = new ArrayList<>(tasks);
            for (int i = 0; i < tasks; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();

                    String input = (idx % everyNthFails == 0) ? ("boom-" + idx) : ("ok-" + idx);

                    long t0 = System.nanoTime();
                    try {
                        ResultWithAgenticScope<String> result = agent.run(input);
                        AgenticScope scope = result.agenticScope();
                        assertThat(scope.readState("calledA", false)).isTrue();
                        assertThat(scope.readState("calledB", false)).isTrue();
                        assertThat(scope.readState("calledC", false)).isTrue();
                        ok.increment();
                    } catch (Exception e) {
                        failed.increment();
                    } finally {
                        nanos.add(System.nanoTime() - t0);
                    }

                    return null;
                }));
            }

            ready.await(15, TimeUnit.SECONDS);
            // release the kraken
            start.countDown();

            for (Future<Void> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }

            // If any async cleanup finishes slightly after futures complete:
            FlowPlannerSessionsAwait.awaitNoSessions(5);

            long total = ok.sum() + failed.sum();
            double avgMs = (nanos.sum() / 1_000_000.0) / Math.max(1, total);

            System.out.println("Parallel run completed: total=" + total
                    + " ok=" + ok.sum()
                    + " failed=" + failed.sum()
                    + " avgMs=" + avgMs
                    + " activeSessions=" + FlowPlannerSessions.getInstance().activeSessionCount());

        } finally {
            pool.shutdownNow();
        }
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }
}
