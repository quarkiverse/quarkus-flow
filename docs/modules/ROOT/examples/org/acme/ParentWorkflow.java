package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

// Static imports recommended for brevity:
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ParentWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("parent-workflow-with-children", "org.acme", "1.0")
                .tasks(
                        // Using workflow(...) shortcut to reference existing workflow
                        subflow("executeHttpWorkflow",
                                workflow("org.acme", "http-with-query-headers", "1.0")),
                        // Using Consumer<WorkflowTaskBuilder> to reference existing workflow
                        subflow("emitEventSubflow",
                                configurer -> configurer.workflow()
                                        .withName("emit-event-workflow")
                                        .withNamespace("org.acme")
                                        .withVersion("1.0")))
                .build();
    }
}
