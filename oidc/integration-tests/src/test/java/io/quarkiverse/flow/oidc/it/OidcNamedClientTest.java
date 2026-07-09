package io.quarkiverse.flow.oidc.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OidcNamedClientTest {

    @Test
    @DisplayName("Named authentication policy negotiates the token through a pre-configured Quarkus OIDC client")
    void test_named_authentication_policy_uses_configured_oidc_client() {
        // The named-client-it workflow references the "keycloak" authentication policy via use("keycloak").
        // That policy is routed to the pre-configured quarkus.oidc-client.keycloak-named client, whose token
        // endpoint (/named/oauth2/token) returns "named-access-token". The downstream stub only accepts
        // Authorization: Bearer named-access-token, so a successful response proves the token was negotiated
        // through the named OIDC client rather than the workflow's inline DSL policy.
        when()
                .get("/quarkus-flow/named/images")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("filename", hasItem("named.jpg"));
    }
}
