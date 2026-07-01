package io.quarkiverse.flow.oidc.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.contains;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OidcScopeCachingTest {

    // Two policies that share authority/client/grant but differ only by scope must NOT share a cached OidcClient.
    // The token stubs key the access_token by the requested scope and the downstream stubs key their answer by that
    // token, so the filename returned proves which scope was actually sent on the token request.

    @Test
    @DisplayName("test_token_request_carries_the_scope_for_each_distinct_policy")
    void test_token_request_carries_the_scope_for_each_distinct_policy() {
        when()
                .get("/quarkus-flow/scoped/read/images")
                .then()
                .statusCode(200)
                .body("filename", contains("read-only.jpg"));

        when()
                .get("/quarkus-flow/scoped/write/images")
                .then()
                .statusCode(200)
                .body("filename", contains("write-only.jpg"));
    }
}
