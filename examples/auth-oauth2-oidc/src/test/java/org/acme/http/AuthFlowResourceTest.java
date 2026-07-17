package org.acme.http;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthFlowResourceTest {

    @Test
    @DisplayName("Should do a request using the access_token provided by the Authorization Server")
    void should_list_images_authorized_with_client_credentials() {

        RestAssured.given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/quarkus-flow/images")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Should use multiple client/authorization servers in the same workflow")
    void should_use_multiple_clients_requesting_token_on_multiple_auth_servers_successfully() {
        // Execute workflow multiple times to ensure OIDC clients are not overridden
        for (int i = 0; i < 3; i++) {
            RestAssured.given()
                    .accept(ContentType.JSON)
                    .contentType(ContentType.JSON)
                    .get("/quarkus-flow/read-all-emails")
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    @DisplayName("Should do a request using the access_token provided by the Authorization Server (using OpenAPI)")
    void should_list_images_using_openapi_with_oauth2_schema() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/quarkus-flow/openapi/images")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Should obtain a token via the OAuth2 Resource Owner Password grant and call the downstream service")
    void should_delete_image_with_password_grant() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/quarkus-flow/password/images")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Should obtain a token via RFC 8693 token exchange (subject + actor) and call the downstream service")
    void should_delete_image_with_token_exchange_grant() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/quarkus-flow/token-exchange/images")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Should attach a token from an injected quarkus-oidc-client OidcClient via the HttpRequestDecorator")
    void should_list_images_with_injected_oidc_client() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/quarkus-flow/oidc/images")
                .then()
                .statusCode(200);
    }

}