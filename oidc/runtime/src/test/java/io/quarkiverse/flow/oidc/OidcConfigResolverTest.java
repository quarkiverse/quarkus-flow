package io.quarkiverse.flow.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class OidcConfigResolverTest {

    private static final String PREFIX = "quarkus.flow.oidc.";
    private static final WorkflowDefinitionId WORKFLOW_ID = new WorkflowDefinitionId("ns", "wf", "1.0.0");

    @Test
    @DisplayName("Empty config falls back to global defaults with all properties empty")
    void test_no_overrides_returns_global_defaults() {
        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(Map.of()));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "myTask", null);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Global defaults are returned when no workflow or task override matches")
    void test_global_defaults_are_returned_when_no_specific_override() {
        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(Map.of(
                PREFIX + "auth-server-url", "https://global-auth.example.com",
                PREFIX + "access-token-expires-in", "PT300S",
                PREFIX + "headers.X-Tenant", "default")));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "task1", null);

        assertThat(result.authServerUrl()).contains("https://global-auth.example.com");
        assertThat(result.accessTokenExpiresIn()).contains(Duration.ofSeconds(300));
        assertThat(result.headers()).containsEntry("X-Tenant", "default");
    }

    @Test
    @DisplayName("Task-level override wins over global and versionless without merging")
    void test_task_level_override_wins_over_all_others() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "auth-server-url", "https://global.example.com");
        props.put(PREFIX + "access-token-expires-in", "PT100S");
        props.put(PREFIX + "headers.X-Global", "g");
        props.put(PREFIX + "client.\"ns:wf\".auth-server-url", "https://versionless.example.com");
        props.put(PREFIX + "client.\"ns:wf:1.0.0:callApi\".auth-server-url", "https://task.example.com");
        props.put(PREFIX + "client.\"ns:wf:1.0.0:callApi\".access-token-expires-in", "PT900S");
        props.put(PREFIX + "client.\"ns:wf:1.0.0:callApi\".headers.X-Task", "t");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "callApi", null);

        assertThat(result.authServerUrl()).contains("https://task.example.com");
        assertThat(result.accessTokenExpiresIn()).contains(Duration.ofSeconds(900));
        assertThat(result.headers()).containsEntry("X-Task", "t");
        assertThat(result.headers()).doesNotContainKey("X-Global");
    }

    @Test
    @DisplayName("Versioned workflow override wins when no task-level key matches")
    void test_versioned_override_wins_when_no_task_match() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.\"ns:wf\".auth-server-url", "https://versionless.example.com");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".auth-server-url", "https://versioned.example.com");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "noMatchTask", null);

        assertThat(result.authServerUrl()).contains("https://versioned.example.com");
    }

    @Test
    @DisplayName("Versionless workflow override wins when no versioned or task key matches")
    void test_versionless_override_wins_when_no_versioned_or_task_match() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.\"ns:wf\".auth-server-url", "https://versionless.example.com");
        props.put(PREFIX + "client.\"ns:wf\".access-token-expires-in", "PT600S");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "task1", null);

        assertThat(result.authServerUrl()).contains("https://versionless.example.com");
        assertThat(result.accessTokenExpiresIn()).contains(Duration.ofSeconds(600));
    }

    @Test
    @DisplayName("Named auth policy override is used when no workflow-level key matches")
    void test_named_auth_policy_is_used_when_no_workflow_match() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.keycloak-prod.auth-server-url", "https://keycloak-prod.example.com");
        props.put(PREFIX + "client.keycloak-prod.access-token-expires-in", "PT500S");
        props.put(PREFIX + "client.keycloak-prod.headers.X-Auth", "named");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "task1", "keycloak-prod");

        assertThat(result.authServerUrl()).contains("https://keycloak-prod.example.com");
        assertThat(result.accessTokenExpiresIn()).contains(Duration.ofSeconds(500));
        assertThat(result.headers()).containsEntry("X-Auth", "named");
    }

    @Test
    @DisplayName("Versionless workflow override takes precedence over named auth policy")
    void test_workflow_override_wins_over_named_auth_policy() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.keycloak-prod.auth-server-url", "https://named.example.com");
        props.put(PREFIX + "client.\"ns:wf\".auth-server-url", "https://workflow.example.com");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, null, "keycloak-prod");

        assertThat(result.authServerUrl()).contains("https://workflow.example.com");
    }

    @Test
    @DisplayName("Null task name skips task-level lookup entirely")
    void test_null_task_name_skips_task_level() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.\"ns:wf:1.0.0:myTask\".auth-server-url", "https://task-auth.example.com");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, null, null);

        assertThat(result.authServerUrl()).isEmpty();
    }

    @Test
    @DisplayName("Blank task name is treated as absent and skips task-level lookup")
    void test_blank_task_name_skips_task_level() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.\"ns:wf:1.0.0:myTask\".auth-server-url", "https://task-auth.example.com");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, "  ", null);

        assertThat(result.authServerUrl()).isEmpty();
    }

    @Test
    @DisplayName("Blank auth policy name is treated as absent and skips named lookup")
    void test_blank_auth_policy_name_skips_named_level() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.keycloak-prod.auth-server-url", "https://named.example.com");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, null, "  ");

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("All identity properties (auth-server-url, client-id, scopes, etc.) can be overridden")
    void test_all_identity_properties_can_be_overridden() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.\"ns:wf\".auth-server-url", "https://auth.example.com");
        props.put(PREFIX + "client.\"ns:wf\".token-path", "/custom/token");
        props.put(PREFIX + "client.\"ns:wf\".discovery-enabled", "true");
        props.put(PREFIX + "client.\"ns:wf\".client-id", "my-client");
        props.put(PREFIX + "client.\"ns:wf\".client-secret", "my-secret");
        props.put(PREFIX + "client.\"ns:wf\".client-secret-method", "BASIC");
        props.put(PREFIX + "client.\"ns:wf\".grant-type", "password");
        props.put(PREFIX + "client.\"ns:wf\".scopes", "read,write");
        props.put(PREFIX + "client.\"ns:wf\".audience", "api");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, null, null);

        assertThat(result.authServerUrl()).contains("https://auth.example.com");
        assertThat(result.tokenPath()).contains("/custom/token");
        assertThat(result.discoveryEnabled()).contains(true);
        assertThat(result.clientId()).contains("my-client");
        assertThat(result.clientSecret()).contains("my-secret");
        assertThat(result.clientSecretMethod()).contains("BASIC");
        assertThat(result.grantType()).contains("password");
        assertThat(result.scopes()).contains(List.of("read", "write"));
        assertThat(result.audience()).contains(List.of("api"));
    }

    @Test
    @DisplayName("All operational tuning properties (expiry, skew, headers, refresh) can be overridden")
    void test_all_tuning_properties_can_be_overridden() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".access-token-expires-in", "PT300S");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".access-token-expiry-skew", "PT10S");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".refresh-token-time-skew", "PT5S");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".absolute-expires-in", "true");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".early-tokens-acquisition", "false");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".headers.X-Custom", "val");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".refresh-interval", "PT5M");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        OidcConfigResolver.ResolvedOverride result = resolver.resolve(WORKFLOW_ID, null, null);

        assertThat(result.accessTokenExpiresIn()).contains(Duration.ofSeconds(300));
        assertThat(result.accessTokenExpirySkew()).contains(Duration.ofSeconds(10));
        assertThat(result.refreshTokenTimeSkew()).contains(Duration.ofSeconds(5));
        assertThat(result.absoluteExpiresIn()).contains(true);
        assertThat(result.earlyTokensAcquisition()).contains(false);
        assertThat(result.headers()).containsEntry("X-Custom", "val");
        assertThat(result.refreshInterval()).contains(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Full priority cascade: task > versioned > versionless > named auth > global")
    void test_priority_order_task_then_versioned_then_versionless_then_named_then_global() {
        Map<String, String> props = new HashMap<>();
        props.put(PREFIX + "auth-server-url", "https://global.example.com");
        props.put(PREFIX + "client.myAuth.auth-server-url", "https://named.example.com");
        props.put(PREFIX + "client.\"ns:wf\".auth-server-url", "https://versionless.example.com");
        props.put(PREFIX + "client.\"ns:wf:1.0.0\".auth-server-url", "https://versioned.example.com");
        props.put(PREFIX + "client.\"ns:wf:1.0.0:callApi\".auth-server-url", "https://task.example.com");

        OidcConfigResolver resolver = new OidcConfigResolver(buildConfig(props));

        // With all layers present, task wins
        assertThat(resolver.resolve(WORKFLOW_ID, "callApi", "myAuth").authServerUrl())
                .contains("https://task.example.com");

        // Without task match, versioned wins
        assertThat(resolver.resolve(WORKFLOW_ID, "otherTask", "myAuth").authServerUrl())
                .contains("https://versioned.example.com");

        // Without versioned match, versionless wins
        WorkflowDefinitionId otherVersion = new WorkflowDefinitionId("ns", "wf", "2.0.0");
        assertThat(resolver.resolve(otherVersion, "task1", "myAuth").authServerUrl())
                .contains("https://versionless.example.com");

        // Without any workflow match, named wins
        WorkflowDefinitionId otherWorkflow = new WorkflowDefinitionId("ns", "other", "1.0.0");
        assertThat(resolver.resolve(otherWorkflow, "task1", "myAuth").authServerUrl())
                .contains("https://named.example.com");

        // Without any match, global wins
        assertThat(resolver.resolve(otherWorkflow, "task1", null).authServerUrl())
                .contains("https://global.example.com");
    }

    private static FlowOidcConfig buildConfig(Map<String, String> properties) {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withMapping(FlowOidcConfig.class)
                .withDefaultValues(properties)
                .build();
        return config.getConfigMapping(FlowOidcConfig.class);
    }
}
