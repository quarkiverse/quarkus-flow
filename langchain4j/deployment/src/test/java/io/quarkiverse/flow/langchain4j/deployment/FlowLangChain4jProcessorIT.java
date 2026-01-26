package io.quarkiverse.flow.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.langchain4j.Agents;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class FlowLangChain4jProcessorIT {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agents.class));

    @Test
    void shouldCreateCompleteDefinition() {
        InstanceHandle<Agents.EveningPlannerAgent> plannerInstance = Arc.container().instance(Agents.EveningPlannerAgent.class);
        assertThat(plannerInstance.isAvailable()).isTrue();

        Agents.EveningPlannerAgent planner = plannerInstance.get();
        List<Agents.EveningPlan> plan = planner.plan("nostalgic");
        assertThat(plan).isNotEmpty();

        WorkflowDefinitionId expectedId = WorkflowNameUtils.newId(Agents.EveningPlannerAgent.class);
        WorkflowRegistry registry = WorkflowRegistry.current();
        Optional<WorkflowDefinition> definition = registry.lookup(expectedId);
        assertThat(definition).isPresent();
        assertThat(definition.get().workflow().getDo()).isNotEmpty();
    }

}
