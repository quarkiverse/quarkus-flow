package io.quarkiverse.flow.oidc.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Deployment test that verifies OIDC client configuration is automatically generated
 * from workflow authentication policies at build time.
 */
public class OidcClientConfigGenerationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .withApplicationRoot((jar) -> jar
                    .addAsResource("flow/oauth2-test-workflow.yaml")
                    .addAsResource("flow/oidc-test-workflow.yaml")
                    .addAsResource("flow/named-auth-workflow.yaml"))
            .overrideConfigKey("quarkus.keycloak.devservices.enabled", "false");

    @Inject
    Config config;

    @Inject
    OidcClients oidcClients;

    @Test
    void oauth2_inline_config_is_generated() {
        // OAuth2 inline auth creates a client named: namespace:name:version.task.taskName
        String clientName = "test:oauth2-workflow:1.0.0.task.callApi";

        // Verify config was generated
        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".auth-server-url", String.class))
                .isPresent()
                .hasValue("http://localhost:8089");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".discovery-enabled", String.class))
                .isPresent()
                .hasValue("false");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".token-path", String.class))
                .isPresent()
                .hasValue("/oauth2/token");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".client-id", String.class))
                .isPresent()
                .hasValue("test-client");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".grant.type", String.class))
                .isPresent()
                .hasValue("client");

        // Verify OidcClient bean is available in Arc
        OidcClient client = oidcClients.getClient(clientName);
        assertThat(client).isNotNull();
    }

    @Test
    void oidc_inline_config_is_generated() {
        // OIDC inline auth creates a client named: namespace:name:version.task.taskName
        String clientName = "test:oidc-workflow:1.0.0.task.callSecureApi";

        // Verify config was generated
        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".auth-server-url", String.class))
                .isPresent()
                .hasValue("http://localhost:8089");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".discovery-enabled", String.class))
                .isPresent()
                .hasValue("true");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".client-id", String.class))
                .isPresent()
                .hasValue("oidc-client");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".grant.type", String.class))
                .isPresent()
                .hasValue("client");

        // Token path should NOT be set for OIDC (uses discovery)
        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".token-path", String.class))
                .isEmpty();

        // Verify OidcClient bean is available in Arc
        OidcClient client = oidcClients.getClient(clientName);
        assertThat(client).isNotNull();
    }

    @Test
    void named_auth_config_is_generated() {
        // Named auth uses the policy name directly
        String clientName = "keycloak";

        // Verify config was generated
        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".auth-server-url", String.class))
                .isPresent()
                .hasValue("http://localhost:8089");

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".discovery-enabled", String.class))
                .isPresent()
                .hasValue("false"); // Named OAuth2 uses OAuth2 (not OIDC discovery)

        assertThat(config.getOptionalValue("quarkus.oidc-client.\"" + clientName + "\".client-id", String.class))
                .isPresent()
                .hasValue("named-client");

        // Verify OidcClient bean is available in Arc
        OidcClient client = oidcClients.getClient(clientName);
        assertThat(client).isNotNull();
    }
}
