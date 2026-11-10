package io.quarkiverse.persistence.jpa.deployment;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.persistence.jpa.JpaInstanceOperations;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusExtensionTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.persistence.CompletedTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceWorkflowInfo;

/**
 * A task that ran more than once has one row per iteration; reading the instance must not fail on them.
 */
@DisabledOnOs(OS.WINDOWS)
public class JPALoopedTaskReadTest {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(LoopThenListenWorkflow.class))
            .withConfigurationResource("durable-application.properties");

    @Inject
    LoopThenListenWorkflow flow;

    @Inject
    PersistenceInstanceHandlers handlers;

    @Inject
    JpaInstanceOperations operations;

    @Test
    void instanceWithARepeatedTaskCanBeRead() {
        WorkflowInstance instance = flow.instance(0);
        instance.start();
        await().atMost(Duration.ofSeconds(20)).until(() -> instance.status() == WorkflowStatus.WAITING);

        assertThat(handlers.reader().find(flow.definition(), instance.id())).isPresent();

        PersistenceWorkflowInfo info = QuarkusTransaction.requiringNew()
                .call(() -> operations.readWorkflowInfo(flow.definition(), instance.id()).orElseThrow());
        assertThat(info.tasks())
                .hasEntrySatisfying("do/0/increment", task -> assertThat(task)
                        .isInstanceOfSatisfying(CompletedTaskInfo.class, completed -> {
                            assertThat(completed.iteration()).isEqualTo(3);
                            assertThat(completed.model().as(Integer.class)).contains(3);
                        }));

        instance.cancel();
    }

    @ApplicationScoped
    public static class LoopThenListenWorkflow extends Flow {

        @Override
        public Workflow descriptor() {
            return FlowWorkflowBuilder.workflow("loopThenListen")
                    .tasks(
                            function("increment", (Integer n) -> n + 1, Integer.class),
                            switchWhenOrElse("again", (Integer n) -> n < 3, "increment", "waitForever", Integer.class),
                            listen("waitForever", toOne("org.acme.never")))
                    .build();
        }
    }
}
