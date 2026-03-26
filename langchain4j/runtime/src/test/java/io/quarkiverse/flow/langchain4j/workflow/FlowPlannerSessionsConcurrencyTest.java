package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FlowPlannerSessionsConcurrencyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowPlannerSessionsConcurrencyTest.class.getName());

    @Inject
    WorkflowRegistry registry;

    private int baselineSessions;

    @BeforeEach
    void captureBaseline() {
        // Record how many sessions exist BEFORE this test starts.
        // This isolates the test from other parallel executions in the same JVM.
        baselineSessions = FlowPlannerSessions.getInstance().activeSessionCount();
        LOGGER.info("Baseline sessions: {}", baselineSessions);
    }

    @AfterEach
    void noLeaks() {
        await()
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> FlowPlannerSessions.getInstance().activeSessionCount() <= baselineSessions);
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

        int tasks = 40;
        // Keep contention moderate to reduce CI flakiness on low-core runners
        int threads = 4;
        int everyNthFails = 8;

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

            ready.await(30, TimeUnit.SECONDS);
            // release the kraken
            start.countDown();

            for (Future<Void> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }

            // If any async cleanup finishes slightly after futures complete:
            FlowPlannerSessionsAwait.awaitNoSessions(10);

            long total = ok.sum() + failed.sum();
            double avgMs = (nanos.sum() / 1_000_000.0) / Math.max(1, total);

            LOGGER.info("Parallel run completed: total={} ok={} failed={} avgMs={} activeSessions={}", total, ok.sum(),
                    failed.sum(), avgMs, FlowPlannerSessions.getInstance().activeSessionCount());

        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }
}
