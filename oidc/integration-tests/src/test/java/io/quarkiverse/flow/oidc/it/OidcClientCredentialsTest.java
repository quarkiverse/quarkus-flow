package io.quarkiverse.flow.oidc.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OidcClientCredentialsTest {

    @Test
    @DisplayName("test_client_credentials_token_is_negotiated_and_attached_as_bearer")
    void test_client_credentials_token_is_negotiated_and_attached_as_bearer() {
        // The downstream image-service stub only answers 200 when it receives 'Authorization: Bearer <token>',
        // so a successful list proves quarkus-oidc-client negotiated the token and Flow attached it.
        when()
                .get("/quarkus-flow/images")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("filename", hasItem("mcruzdev.jpg"));
    }
}
