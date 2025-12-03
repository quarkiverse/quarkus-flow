package io.quarkiverse.flow.deployment.test.devui;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

/**
 * Minimal workflow that is "agentic" from the Dev UI POV:
 * it has bean-invoker metadata, so executions are routed via a CDI bean.
 */
@ApplicationScoped
public class AgenticDevUIWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        Workflow wf = FuncWorkflowBuilder
                .workflow("devui-agentic")
                .document(d -> d.name("agenticDevUI")
                        .summary("Agentic Dev UI workflow backed by a CDI bean"))
                .tasks(set("{}")) // irrelevant, not being called
                .build();

        try {
            WorkflowInvocationMetadata.setBeanInvoker(
                    wf,
                    DevUIAgenticServiceBean.class,
                    DevUIAgenticServiceBean.class.getMethod("complex", String.class, int.class, boolean.class),
                    "lc4j-agentic" // kind (free-form, internal)
            );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to resolve DevUIAgenticService#complex(String)", e);
        }

        return wf;
    }
}
