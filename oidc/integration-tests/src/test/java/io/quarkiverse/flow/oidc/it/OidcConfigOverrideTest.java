package io.quarkiverse.flow.oidc.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OidcConfigOverrideTest {

    @Test
    @DisplayName("Per-workflow config override redirects token endpoint to a different path")
    void test_config_override_redirects_token_endpoint_for_specific_workflow() {
        // The config-override-it workflow's token endpoint is overridden to /overridden/oauth2/token via
        // application.properties. The downstream stub only accepts Authorization: Bearer overridden-access-token,
        // which is what the /overridden/oauth2/token stub returns. A successful response proves the config
        // override was applied and the token was negotiated from the overridden endpoint.
        when()
                .get("/quarkus-flow/config-override/images")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("filename", hasItem("overridden.jpg"));
    }
}
