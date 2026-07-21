package io.quarkiverse.flow.deployment.test.devui;

import static io.quarkiverse.flow.dsl.FlowDSL.set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Minimal workflow that is "agentic" from the Dev UI POV:
 * it has bean-invoker metadata, so executions are routed via a CDI bean.
 */
@ApplicationScoped
public class AgenticDevUIWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        Workflow wf = FlowWorkflowBuilder
                .workflow("devui-agentic")
                .tasks(set("${ . }"))
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
