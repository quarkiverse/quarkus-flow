package io.quarkiverse.flow.devui;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.mermaid.Mermaid;
import io.smallrye.common.annotation.Blocking;

public class WorkflowRPCService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonRpcDescription("Get numbers of workflows available in the application")
    public int getNumbersOfWorkflows() {
        return Arc.container().listAll(WorkflowDefinition.class).size();
    }

    @JsonRpcDescription("Get info about workflows")
    public List<WorkflowInfo> getWorkflows() {
        return Arc.container().listAll(WorkflowDefinition.class).stream()
                .map(w -> new WorkflowInfo(w.get().workflow().getDocument().getName(),
                        w.get().workflow().getDocument().getSummary()))
                .toList();
    }

    @JsonRpcDescription("Generate a mermaid diagram from the workflow's definition")
    public MermaidDefinition generateMermaidDiagram(
            @JsonRpcDescription("Workflow's name") String workflowName) {
        Log.info("Generating diagram for workflow: " + workflowName);
        WorkflowDefinition workflowDefinition = findWorkflowDefinitionByName(workflowName);
        return new MermaidDefinition(new Mermaid().from(workflowDefinition.workflow()));
    }

    @Blocking
    @JsonRpcDescription("Execute a workflow given an input")
    public WorkflowOutput executeWorkflow(
            @JsonRpcDescription("Workflow's name") String workflowName,
            @JsonRpcDescription("Workflow's input") String input) {

        WorkflowDefinition workflowDefinition = findWorkflowDefinitionByName(workflowName);

        WorkflowModel wm = workflowDefinition.instance(parseStringIfNeeded(input))
                .start()
                .join();

        Object obj = wm.asJavaObject();

        return new WorkflowOutput(obj instanceof String ? "text/plain" : "application/json", obj);
    }

    /**
     * Finds {@link WorkflowDefinition} by the given workflow name.
     */
    private WorkflowDefinition findWorkflowDefinitionByName(final String workflowName) {
        return Arc.container().listAll(WorkflowDefinition.class)
                .stream()
                .filter(w -> w.get().workflow().getDocument().getName().equals(workflowName))
                .findFirst().map(InstanceHandle::get)
                .orElseThrow(
                        () -> new IllegalStateException("Workflow with name '" + workflowName + "'" + "not found"));
    }

    /**
     * Parses the input string as JSON if possible, otherwise returns the original string.
     */
    private Object parseStringIfNeeded(String str) {
        try {
            return OBJECT_MAPPER.readValue(str, Map.class);
        } catch (Exception e) {
            // Not a JSON string, return as is
            return str;
        }
    }

    public record MermaidDefinition(String mermaid) {
    }

    public record WorkflowInfo(String name, String description) {
    }

    public record WorkflowOutput(String mimetype, Object data) {
    }
}
