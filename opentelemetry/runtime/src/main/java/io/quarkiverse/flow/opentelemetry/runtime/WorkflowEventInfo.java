package io.quarkiverse.flow.opentelemetry.runtime;

import io.serverlessworkflow.impl.lifecycle.EventType;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

record WorkflowEventInfo(
        String wfApplicationId,
        String wfNamespace,
        String wfName,
        String wfVersion,
        String wfInstanceId,
        EventType eventType) {

    public static WorkflowEventInfo from(WorkflowEvent ev) {
        var context = ev.workflowContext();
        var definition = context.definition();

        return new WorkflowEventInfo(
                definition.application().id(),
                definition.id().namespace(),
                definition.id().name(),
                definition.id().version(),
                context.instanceData().id(),
                ev.type());
    }

    WorkflowEventInfo(String wfApplicationId, String wfNamespace, String wfName, String wfVersion, String wfInstanceId,
            EventType eventType) {
        this.wfApplicationId = wfApplicationId;
        this.wfNamespace = wfNamespace;
        this.wfName = wfName;
        this.wfVersion = wfVersion;
        this.wfInstanceId = wfInstanceId;
        this.eventType = eventType;
    }
}
