package io.quarkiverse.flow.opentelemetry.runtime;

import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

record WorkflowEventInfo(
        String wfApplicationId,
        String wfNamespace,
        String wfName,
        String wfVersion,
        String wfInstanceId,
        WorkflowEventType eventType) {
    public static WorkflowEventInfo from(WorkflowEvent ev) {
        var context = ev.workflowContext();
        var definition = context.definition();

        return new WorkflowEventInfo(
                definition.application().id(),
                definition.id().namespace(),
                definition.id().name(),
                definition.id().version(),
                context.instanceData().id(),
                WorkflowEventType.fromEvent(ev));
    }
}
