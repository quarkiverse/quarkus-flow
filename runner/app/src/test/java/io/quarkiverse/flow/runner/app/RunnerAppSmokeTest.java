package io.quarkiverse.flow.runner.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(RunnerAppSmokeTest.SmokeTestProfile.class)
class RunnerAppSmokeTest {

    @Test
    @DisplayName("runner_api_is_accessible")
    void runner_api_is_accessible() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("openapi_endpoint_is_available")
    void openapi_endpoint_is_available() {
        given()
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    @DisplayName("swagger_ui_is_accessible")
    void swagger_ui_is_accessible() {
        given()
                .when().get("/q/swagger-ui")
                .then()
                .statusCode(200);
    }

    /**
     * Test profile with unique database path to avoid MVStore file locks.
     */
    public static class SmokeTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Use unique database file per test class to avoid MVStore file locks
            String uniqueDbPath = "target/test-db-smoke-" + System.currentTimeMillis() + "-" +
                    System.nanoTime() + ".mv";
            return Map.of(
                    "quarkus.flow.persistence.mvstore.db-path", uniqueDbPath);
        }
    }
}
