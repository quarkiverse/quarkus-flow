package io.quarkiverse.flow.opentelemetry.it;

import static io.quarkiverse.flow.opentelemetry.it.util.Utils.getWorkflowSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.opentelemetry.it.util.SpanInfo;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * A cancelled instance must still end and export its workflow span, exactly once.
 * <p>
 * Both cancel orders are covered. Cancelling an instance waiting on a listen task cancels the future the listen task
 * registered, which makes the engine clear the instance metadata before it publishes the cancelled event. A wait task
 * registers no such future, so cancelling an instance waiting on one publishes the cancelled event first, and the
 * metadata is cleared later.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1058">#1058</a>
 */
@QuarkusIntegrationTest
class CancelWorkflowIT extends OTelBaseIT {

    private static final int RUNS = 5;
    private static final String LISTEN_WORKFLOW = "otel-listen-task";
    private static final String WAIT_WORKFLOW = "otel-wait-task";
    private static final String WORKFLOW_CANCELLED = "io.serverlessworkflow.workflow.cancelled.v1";

    @Override
    String workflowName() {
        return LISTEN_WORKFLOW;
    }

    @Test
    @DisplayName("cancelling_a_waiting_listen_task_instance_exports_its_workflow_span")
    void cancellingAWaitingListenTaskInstanceExportsItsWorkflowSpan() {
        assertCancelledInstancesExportTheirWorkflowSpan(LISTEN_WORKFLOW, "do/0/listenTask");
    }

    @Test
    @DisplayName("cancelling_a_waiting_wait_task_instance_exports_its_workflow_span_once")
    void cancellingAWaitingWaitTaskInstanceExportsItsWorkflowSpanOnce() {
        assertCancelledInstancesExportTheirWorkflowSpan(WAIT_WORKFLOW, "do/0/waitTask1");
    }

    private void assertCancelledInstancesExportTheirWorkflowSpan(String workflowName, String cancelledTaskId) {
        var instanceIds = IntStream.range(0, RUNS)
                .mapToObj(i -> startAndCancel(workflowName))
                .toList();

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var spansByInstance = getWorkflowSpans(workflowName, workVersion())
                            .stream()
                            .collect(Collectors.groupingBy(SpanInfo::getWorkflowId));

                    assertThat(instanceIds)
                            .allSatisfy(instanceId -> assertThat(spansByInstance.getOrDefault(instanceId, List.of()))
                                    .as("spans of cancelled instance %s", instanceId)
                                    .satisfiesExactlyInAnyOrder(
                                            createSpan -> assertThat(createSpan)
                                                    .returns("workflow.create " + workflowName, SpanInfo::getName),
                                            taskSpan -> assertThat(taskSpan)
                                                    .returns(cancelledTaskId, SpanInfo::getTaskId),
                                            executeSpan -> assertThat(executeSpan)
                                                    .returns("workflow.execute " + workflowName, SpanInfo::getName)
                                                    .extracting(SpanInfo::getEventNames, list(String.class))
                                                    .containsOnlyOnce(WORKFLOW_CANCELLED)));
                });
    }

    private static String startAndCancel(String workflowName) {
        return RestAssured.given()
                .accept(ContentType.JSON)
                .post("/otel-workflows/" + workflowName + "/cancel")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("id");
    }
}
