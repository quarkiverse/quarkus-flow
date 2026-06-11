package org.acme.flow;

import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkiverse.flow.testing.assertions.AsyncFlowAssertions;
import io.quarkiverse.flow.testing.assertions.FlowAssertions;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
public class SwitchLoopWaitTest {

    @Inject
    WorkflowEventStore eventStore;

    @Test
    void let_run_max_count_5_test_switch_loop_wait_with_flow_assertions() {

        Map<String, Object> response = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(new FlowRequest(5))
                .post("/api/flow/start")
                .then()
                .statusCode(202)
                .extract()
                .body()
                .as(new TypeRef<Map<String, Object>>() {
                });

        AsyncFlowAssertions.assertWith(eventStore)
                .filteringBy(((String) response.get("instanceId")))
                .workflowStarted()
                .taskCompleted("inc")
                .taskCompleted("looping")
                .taskCompleted("waitABit")
                .workflowCompleted();

        AsyncFlowAssertions.assertWith(eventStore)
                .strictly()
                .filteringBy(((String) response.get("instanceId")))
                .workflowStarted()
                .taskCompleted("looping")
                .taskCompleted("inc")
                .taskCompleted("waitABit")
                .workflowCompleted();
    }
}