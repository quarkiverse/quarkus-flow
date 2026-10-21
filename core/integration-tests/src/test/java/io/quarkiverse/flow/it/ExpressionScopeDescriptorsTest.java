package io.quarkiverse.flow.it;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
public class ExpressionScopeDescriptorsTest {

    @Test
    @DisplayName("direct_reference_to_workflow_task_runtime_scopes_resolves_in_jvm_mode")
    void direct_reference_to_workflow_task_runtime_scopes_resolves_in_jvm_mode() {
        RestAssured.given()
                .get("/expression-scope-descriptors/direct")
                .then()
                .statusCode(200)
                .body(containsString("describeContext"));
    }

    @Test
    @DisplayName("for_loop_variable_named_task_shadows_builtin_task_scope_in_jvm_mode")
    void for_loop_variable_named_task_shadows_builtin_task_scope_in_jvm_mode() {
        RestAssured.given()
                .get("/expression-scope-descriptors/for-loop")
                .then()
                .statusCode(200);
    }
}
