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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests concurrent workflow execution with session management.
 * <p>
 * This test MUST run in isolation because it verifies global FlowPlannerSessions state,
 * which is a singleton shared across the JVM. Running concurrently with other tests
 * would cause race conditions in session counting.
 */
@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
class FlowPlannerSessionsConcurrencyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowPlannerSessionsConcurrencyTest.class.getName());

    @Inject
    WorkflowRegistry registry;

    @AfterEach
    void ensureNoLeakedSessions() {
        await()
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(FlowPlannerSessions.getInstance().activeSessionCount())
                        .withFailMessage("Leaked sessions: %s", FlowPlannerSessions.getInstance().activeSessionIds())
                        .isZero());
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
                    boolean expectFailure = input.startsWith("boom");

                    long t0 = System.nanoTime();
                    try {
                        ResultWithAgenticScope<String> result = agent.run(input);

                        // If we expected failure but got success, that's wrong
                        if (expectFailure) {
                            failed.increment();
                            LOGGER.warn("Expected failure for input {} but got success", input);
                        } else {
                            // Only assert agent states for successful workflows
                            AgenticScope scope = result.agenticScope();
                            assertThat(scope.readState("calledA", false)).isTrue();
                            assertThat(scope.readState("calledB", false)).isTrue();
                            assertThat(scope.readState("calledC", false)).isTrue();
                            ok.increment();
                        }
                    } catch (Exception e) {
                        if (expectFailure) {
                            ok.increment(); // Expected failure
                        } else {
                            failed.increment();
                            LOGGER.warn("Unexpected failure for input {}: {}", input, e.getMessage());
                        }
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

            long total = ok.sum() + failed.sum();
            double avgMs = (nanos.sum() / 1_000_000.0) / Math.max(1, total);

            int activeSessions = FlowPlannerSessions.getInstance().activeSessionCount();
            LOGGER.info("Parallel run completed: total={} ok={} failed={} avgMs={} activeSessions={}",
                    total, ok.sum(), failed.sum(), avgMs, activeSessions);

            if (activeSessions > 0) {
                LOGGER.warn("Still have {} active sessions after all futures completed: {}",
                        activeSessions, FlowPlannerSessions.getInstance().activeSessionIds());
            }

            // Session cleanup verification happens in @AfterEach
            // We don't check it here because sessions may still be cleaning up asynchronously

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
