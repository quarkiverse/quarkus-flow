package io.quarkiverse.flow.deployment.test;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.deployment.WorkflowNamingConverter;

class WorkflowNamingConverterTest {

    @Test
    void namespaceToPackage() {
        String finalNamespace = WorkflowNamingConverter.namespaceToPackage(
                "my-namespace-test");
        Assertions.assertEquals("my.namespace.test", finalNamespace);
    }

    @Test
    void namespaceToPackage_should_generate_correctly_with_uppercase() {
        String finalNamespace = WorkflowNamingConverter.namespaceToPackage(
                "Cncf-Namespace");
        Assertions.assertEquals("cncf.namespace", finalNamespace);
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
                "my-company", "myWorkflow", Optional.empty());
        Assertions.assertEquals("my.company.MyWorkflow", fqcn);
    }

    @Test
    void generateFlowClassIdentifier_should_use_config_namespace_if_present() {
        String fqcn = WorkflowNamingConverter.generateFlowClassIdentifier(
                "my-company", "myWorkflow", Optional.of("custom.namespace"));
        Assertions.assertEquals("custom.namespace.my.company.MyWorkflow", fqcn);
    }
}
