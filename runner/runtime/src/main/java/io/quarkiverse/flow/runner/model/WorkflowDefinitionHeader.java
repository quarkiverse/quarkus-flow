package io.quarkiverse.flow.runner.model;

import java.util.Map;

import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.Workflow;

public record WorkflowDefinitionHeader(String namespace, String name, String version, String dsl, String title,
        String summary, Map<String, Object> metadata, SchemaUnion inputSchema, Map<String, Link> links) {

    public static WorkflowDefinitionHeader from(Workflow workflow) {
        if (workflow == null || workflow.getDocument() == null) {
            throw new IllegalArgumentException("Workflow definition must contain an workflow document");
        }
        final String namespace = workflow.getDocument().getNamespace();
        final String name = workflow.getDocument().getName();
        final String version = workflow.getDocument().getVersion();

        final Links links = Links.empty()
                .self("/runner/definitions/" + namespace + "/" + name + "/" + version)
                .execute("/runner/exec/" + namespace + "/" + name + "/" + version);

        return new WorkflowDefinitionHeader(
                namespace,
                name,
                version,
                workflow.getDocument().getDsl(),
                workflow.getDocument().getTitle(),
                workflow.getDocument().getSummary(),
                workflow.getDocument().getMetadata() == null ? Map.of()
                        : workflow.getDocument().getMetadata().getAdditionalProperties(),
                workflow.getInput() == null ? null : workflow.getInput().getSchema(),
                links.asMap());
    }

}
