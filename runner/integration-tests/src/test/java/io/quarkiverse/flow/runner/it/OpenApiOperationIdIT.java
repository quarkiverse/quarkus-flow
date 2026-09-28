package io.quarkiverse.flow.runner.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Regression test for duplicated operation IDs when the application uses the METHOD
 * operation ID strategy. Overloaded resource methods used to produce the same operationId.
 */
@QuarkusTest
@TestProfile(OperationIdMethodStrategyProfile.class)
@DisplayName("OpenAPI operation IDs with METHOD strategy")
class OpenApiOperationIdIT {

    @Test
    @DisplayName("test_openapi_operation_ids_are_unique")
    void test_openapi_operation_ids_are_unique() {
        Map<String, Map<String, Object>> paths = given()
                .queryParam("format", "json")
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("paths");

        List<String> operationIds = new ArrayList<>();
        paths.values().forEach(pathItem -> pathItem.values().forEach(operation -> {
            if (operation instanceof Map<?, ?> op && op.get("operationId") instanceof String id) {
                operationIds.add(id);
            }
        }));

        assertThat(operationIds)
                .contains("executeLatestWorkflow", "executeWorkflowVersion",
                        "suspendLatestWorkflow", "suspendWorkflowVersion",
                        "resumeLatestWorkflow", "resumeWorkflowVersion",
                        "cancelLatestWorkflow", "cancelWorkflowVersion")
                .doesNotHaveDuplicates();
    }
}
