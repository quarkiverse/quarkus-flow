package io.quarkiverse.flow.deployment.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
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
                "my-company", "myWorkflow");
        Assertions.assertEquals("my.company.MyWorkflow", fqcn);
    }

    @Test
    void generateFlowClassIdentifier_should_use_config_namespace_if_present() {
        String fqcn = WorkflowNamingConverter.generateFlowClassIdentifier(
                "my-company", "myWorkflow", "custom.namespace");
        Assertions.assertEquals("custom.namespace.my.company.MyWorkflow", fqcn);
    }

    @Test
    @DisplayName("versionToPackage_should_convert_basic_semver")
    void versionToPackage_should_convert_basic_semver() {
        String result = WorkflowNamingConverter.versionToPackage("0.1.0");
        Assertions.assertEquals("v0_1_0", result);
    }

    @Test
    @DisplayName("versionToPackage_should_convert_pre_release")
    void versionToPackage_should_convert_pre_release() {
        String result = WorkflowNamingConverter.versionToPackage("1.2.3-alpha.1");
        Assertions.assertEquals("v1_2_3_alpha_1", result);
    }

    @Test
    @DisplayName("versionToPackage_should_strip_build_metadata")
    void versionToPackage_should_strip_build_metadata() {
        String result = WorkflowNamingConverter.versionToPackage("1.0.0+build.1");
        Assertions.assertEquals("v1_0_0", result);
    }

    @Test
    @DisplayName("versionToPackage_should_strip_build_metadata_and_pre_release")
    void versionToPackage_should_strip_build_metadata_and_pre_release() {
        String result = WorkflowNamingConverter.versionToPackage("1.0.0-beta.2+exp.sha.5114f85");
        Assertions.assertEquals("v1_0_0_beta_2", result);
    }

    @Test
    @DisplayName("versionToPackage_should_lowercase_pre_release_identifiers")
    void versionToPackage_should_lowercase_pre_release_identifiers() {
        String result = WorkflowNamingConverter.versionToPackage("2.0.0-RC.1");
        Assertions.assertEquals("v2_0_0_rc_1", result);
    }

    @Test
    @DisplayName("versionToPackage_should_throw_on_null")
    void versionToPackage_should_throw_on_null() {
        Assertions.assertThrows(NullPointerException.class,
                () -> WorkflowNamingConverter.versionToPackage(null));
    }

    @Test
    @DisplayName("versionToPackage_should_throw_on_blank")
    void versionToPackage_should_throw_on_blank() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> WorkflowNamingConverter.versionToPackage("   "));
    }
}
