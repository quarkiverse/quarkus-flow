package io.quarkiverse.flow;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class FlowRunner {

    @Inject
    FlowRegistry registry;

    @Inject
    WorkflowApplication workflowApplication;

    public CompletableFuture<WorkflowModel> start(String id, Map<String, Object> input) {
        Workflow wf = registry.get(id);
        return workflowApplication.workflowDefinition(wf).instance(input).start();
    }

    public CompletableFuture<WorkflowModel> start(Workflow wf, Map<String, Object> input) {
        return workflowApplication.workflowDefinition(wf).instance(input).start();
    }

    public CompletableFuture<WorkflowModel> start(FlowMethod<?> ref, Map<String, Object> input) {
        MethodRef m = LambdaIntrospector.extract(ref);
        Workflow wf = registry.get(m.ownerClass, m.methodName);

        return workflowApplication.workflowDefinition(wf).instance(input).start();
    }

    public CompletableFuture<WorkflowModel> start(FlowSupplier ref, Map<String, Object> input) {
        Workflow wf = registry.get(LambdaIntrospector.extract(ref));
        return start(wf, input);
    }

}
