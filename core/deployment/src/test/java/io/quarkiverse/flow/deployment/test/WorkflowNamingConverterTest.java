package io.quarkiverse.flow.deployment.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.deployment.WorkflowNamingConverter;

class WorkflowNamingConverterTest {

    @Test
    void namespaceToPackage() {
        String finalNamespace = WorkflowNamingConverter.namespaceToPackage(
                "io.quarkiverse.flow.generated",
                "my-namespace-test");
        Assertions.assertEquals("io.quarkiverse.flow.generated.my.namespace.test", finalNamespace);
    }

    @Test
    void namespaceToPackage_should_generate_correctly_with_uppercase() {
        String finalNamespace = WorkflowNamingConverter.namespaceToPackage(
                "io.quarkiverse.flow.generated",
                "Cncf-Namespace");
        Assertions.assertEquals("io.quarkiverse.flow.generated.cncf.namespace", finalNamespace);
    }

    @Test
    void nameToClassName() {
        String className = WorkflowNamingConverter.nameToClassName("my-workflow-name");
        Assertions.assertEquals("MyWorkflowName", className);
    }

    @Test
    void nameToClassName_should_generate_correctly_with_uppercase() {
        String className = WorkflowNamingConverter.nameToClassName("CNCFWorkflow");
        Assertions.assertEquals("CNCFWorkflow", className);
    }

    @Test
    void nameToClassName_should_generate_correctly() {
        String className = WorkflowNamingConverter.nameToClassName("wonderfulworkflow");
        Assertions.assertEquals("Wonderfulworkflow", className);
    }

    @Test
    void generateFlowClassIdentifier_should_generate_correctly() {
        String fqcn = WorkflowNamingConverter.generateFlowClassIdentifier(
                "my-company", "myWorkflow", FlowDefinitionsConfig.DEFAULT_FLOW_NAMESPACE);
        Assertions.assertEquals("io.quarkiverse.flow.generated.my.company.MyWorkflowWorkflow", fqcn);
    }
}
