package io.quarkiverse.flow.devui;

import java.util.List;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.mermaid.Mermaid;

public class WorkflowRPCService {

    @JsonRpcDescription("Get numbers of workflows available in the application")
    public int getNumbersOfWorkflows() {
        return Arc.container().listAll(WorkflowDefinition.class).size();
    }

    @JsonRpcDescription("Get workflows info")
    public List<WorkflowInfo> getWorkflows() {
        return Arc.container().listAll(WorkflowDefinition.class).stream()
                .map(w -> new WorkflowInfo(w.get().workflow().getDocument().getName(),
                        w.get().workflow().getDocument().getSummary()))
                .toList();
    }

    @JsonRpcDescription("Generate mermaid definition from Workflow Definition")
    public MermaidDefinition generateMermaid(String name) {
        WorkflowDefinition workflowDefinition = Arc.container().listAll(WorkflowDefinition.class)
                .stream()
                .filter(w -> w.get().workflow().getDocument().getName().equals(name))
                .findFirst().map(InstanceHandle::get)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + name));

        return new MermaidDefinition(new Mermaid().from(workflowDefinition.workflow()));
    }

    public record MermaidDefinition(String mermaid) {
    }

    public record WorkflowInfo(String name, String description) {
    }

}
