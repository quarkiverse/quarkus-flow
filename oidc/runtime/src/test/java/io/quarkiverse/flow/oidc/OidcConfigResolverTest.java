package io.quarkiverse.flow.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.DurationConverter;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class OidcConfigResolverTest {

    private static final String P = "quarkus.flow.oidc.";
    private static final WorkflowDefinitionId WORKFLOW_ID = new WorkflowDefinitionId("ns", "wf", "1.0.0");

    @Test
    @DisplayName("Empty config returns no client name")
    void test_no_overrides_returns_empty() {
        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(Map.of()));

        Optional<String> result = resolver.resolve(WORKFLOW_ID, "myTask", null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Task-level override wins over all other levels")
    void test_task_level_wins() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.\"ns:wf\".name", "versionless-client");
        props.put(P + "client.\"ns:wf:1.0.0\".name", "versioned-client");
        props.put(P + "client.\"ns:wf:1.0.0:callApi\".name", "task-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, "callApi", null)).contains("task-client");
    }

    @Test
    @DisplayName("Versioned workflow override wins when no task-level key matches")
    void test_versioned_wins_when_no_task_match() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.\"ns:wf\".name", "versionless-client");
        props.put(P + "client.\"ns:wf:1.0.0\".name", "versioned-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, "noMatchTask", null)).contains("versioned-client");
    }

    @Test
    @DisplayName("Versionless workflow override wins when no versioned or task key matches")
    void test_versionless_wins_when_no_versioned_match() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.\"ns:wf\".name", "versionless-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, "task1", null)).contains("versionless-client");
    }

    @Test
    @DisplayName("Named auth policy override is used when no workflow-level key matches")
    void test_named_auth_wins_when_no_workflow_match() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.keycloak-prod.name", "my-keycloak");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, "task1", "keycloak-prod")).contains("my-keycloak");
    }

    @Test
    @DisplayName("Versionless workflow override takes precedence over named auth policy")
    void test_workflow_wins_over_named_auth() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.keycloak-prod.name", "named-client");
        props.put(P + "client.\"ns:wf\".name", "workflow-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, null, "keycloak-prod")).contains("workflow-client");
    }

    @Test
    @DisplayName("Null task name skips task-level lookup entirely")
    void test_null_task_name_skips_task_level() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.\"ns:wf:1.0.0:myTask\".name", "task-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, null, null)).isEmpty();
    }

    @Test
    @DisplayName("Blank task name is treated as absent and skips task-level lookup")
    void test_blank_task_name_skips_task_level() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.\"ns:wf:1.0.0:myTask\".name", "task-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, "  ", null)).isEmpty();
    }

    @Test
    @DisplayName("Blank auth policy name is treated as absent and skips named lookup")
    void test_blank_auth_policy_name_skips_named_level() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.keycloak-prod.name", "my-keycloak");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        assertThat(resolver.resolve(WORKFLOW_ID, null, "  ")).isEmpty();
    }

    @Test
    @DisplayName("Full priority cascade: task > versioned > versionless > named auth > empty")
    void test_full_priority_cascade() {
        Map<String, String> props = new HashMap<>();
        props.put(P + "client.myAuth.name", "named-client");
        props.put(P + "client.\"ns:wf\".name", "versionless-client");
        props.put(P + "client.\"ns:wf:1.0.0\".name", "versioned-client");
        props.put(P + "client.\"ns:wf:1.0.0:callApi\".name", "task-client");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        // With all layers present, task wins
        assertThat(resolver.resolve(WORKFLOW_ID, "callApi", "myAuth")).contains("task-client");

        // Without task match, versioned wins
        assertThat(resolver.resolve(WORKFLOW_ID, "otherTask", "myAuth")).contains("versioned-client");

        // Without versioned match, versionless wins
        WorkflowDefinitionId otherVersion = new WorkflowDefinitionId("ns", "wf", "2.0.0");
        assertThat(resolver.resolve(otherVersion, "task1", "myAuth")).contains("versionless-client");

        // Without any workflow match, named wins
        WorkflowDefinitionId otherWorkflow = new WorkflowDefinitionId("ns", "other", "1.0.0");
        assertThat(resolver.resolve(otherWorkflow, "task1", "myAuth")).contains("named-client");

        // Without any match, empty
        assertThat(resolver.resolve(otherWorkflow, "task1", null)).isEmpty();
    }

    private static FlowOidcConfig buildConfig(Map<String, String> properties) {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultSources()
                .withConverter(Duration.class, 200, new DurationConverter())
                .withMapping(FlowOidcConfig.class)
                .withDefaultValues(properties)
                .build();
        return config.getConfigMapping(FlowOidcConfig.class);
    }
}
