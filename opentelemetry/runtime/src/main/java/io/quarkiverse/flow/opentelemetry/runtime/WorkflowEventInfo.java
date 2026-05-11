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
        var document = definition.workflow().getDocument();

        return new WorkflowEventInfo(
                definition.application().id(),
                definition.id().namespace(),
                document.getName(),
                document.getVersion(),
                context.instanceData().id(),
                WorkflowEventType.fromEvent(ev));
    }
}
