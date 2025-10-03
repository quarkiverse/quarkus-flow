package io.quarkiverse.flow.devui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.annotations.JsonRpcDescription;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.mermaid.Mermaid;
import io.smallrye.common.annotation.Blocking;

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
        WorkflowDefinition workflowDefinition = findWorkflowDefinitionByName(name);
        return new MermaidDefinition(new Mermaid().from(workflowDefinition.workflow()));
    }

    @JsonRpcDescription("Get Workflow input specification")
    public WorkflowInput getInputSpecification(String workflowName) {
        WorkflowDefinition workflowDefinition = findWorkflowDefinitionByName(workflowName);

        if (notContainsSchema(workflowDefinition)) {
            return new WorkflowInput(false, null);
        }

        return new WorkflowInput(true, workflowDefinition.workflow().getInput().getSchema());
    }

    @Blocking
    @JsonRpcDescription("Execute Workflow given an input")
    public Map<String, Object> executeWorkflow(@JsonRpcDescription("Workflow name") String workflowName,
            @JsonRpcDescription("Workflow input as JSON format") Map<String, Object> inputAsObject,
            @JsonRpcDescription("Workflow input as text form") String inputAsText) {
        WorkflowDefinition workflowDefinition = findWorkflowDefinitionByName(workflowName);

        String finalInputAsText = "";
        if (!Objects.isNull(inputAsText)) {
            finalInputAsText = inputAsText;
        }

        WorkflowModel wm = workflowDefinition.instance(Objects.isNull(inputAsObject) ? finalInputAsText : inputAsObject)
                .start()
                .join();

        if (wm.asJavaObject().getClass().equals(String.class)) {
            return Map.of("output", wm.asText().orElseThrow());
        }

        return wm.asMap().orElseGet(Map::of);
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
     * Verifies if the given {@link WorkflowDefinition} contains schema.
     */
    private boolean notContainsSchema(WorkflowDefinition wd) {
        return wd.workflow().getInput() == null
                || wd.workflow().getInput().getSchema() == null;
    }

    public record MermaidDefinition(String mermaid) {
    }

    public record WorkflowInfo(String name, String description) {
    }

    public record WorkflowInput(boolean hasSchema, SchemaUnion schemaUnion) {
    }
}
