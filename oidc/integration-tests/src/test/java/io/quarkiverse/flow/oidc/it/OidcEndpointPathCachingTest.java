package io.quarkiverse.flow.oidc.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.contains;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OidcEndpointPathCachingTest {

    // Two policies that share authority/client/grant (and have no scope) but differ only by token endpoint path must NOT
    // share a cached OidcClient. Each token stub returns a distinct access_token keyed by its endpoint path, and the
    // downstream stubs key their answer by that token, so the filename returned proves which endpoint actually minted the
    // token. Before the cache key included the token endpoint, both policies collided and the second silently reused the
    // first one's endpoint.

    @Test
    @DisplayName("test_token_request_targets_the_endpoint_path_for_each_distinct_policy")
    void test_token_request_targets_the_endpoint_path_for_each_distinct_policy() {
        when()
                .get("/quarkus-flow/endpoint/a/images")
                .then()
                .statusCode(200)
                .body("filename", contains("from-endpoint-a.jpg"));

        when()
                .get("/quarkus-flow/endpoint/b/images")
                .then()
                .statusCode(200)
                .body("filename", contains("from-endpoint-b.jpg"));
    }
}
