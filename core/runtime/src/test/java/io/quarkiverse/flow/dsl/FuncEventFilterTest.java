package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static io.quarkiverse.flow.dsl.FlowDSL.consumed;
import static io.quarkiverse.flow.dsl.FlowDSL.emitJson;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.to;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;
import static io.quarkiverse.flow.dsl.TestSerializationUtils.writeAndReadInMemory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowModelCollection;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.events.EventPublisher;

/**
 * Tests for the Event Filter DSL specification. Verifies that the fluent builder correctly wires
 * the payload parsing and contextual lambdas into the final Workflow definitions, and ensures the
 * ModelCollection adapters seamlessly convert between types.
 */
class FuncEventFilterTest {

    @Test
    void testListenToOneCollection() throws IOException {
        runIt(
                FlowWorkflowBuilder.workflow("listenToOneReviewCol")
                        .tasks(
                                listen("waitReview", toOne("org.acme.test.review"))
                                        .outputAs((Collection<?> node) -> node.iterator().next()))
                        .build());
    }

    @Test
    void testListenToOneArrayNode() throws IOException {
        runIt(
                FlowWorkflowBuilder.workflow("listenToOneReviewNode")
                        .tasks(
                                listen("waitReview", toOne("org.acme.test.review"))
                                        .outputAs((ArrayNode node) -> node.get(0)))
                        .build());
    }

    @Test
    void testListenToOneList() throws IOException {
        runIt(
                FlowWorkflowBuilder.workflow("listenToOneReviewList")
                        .tasks(
                                listen("waitReview", toOne("org.acme.test.review"))
                                        .outputAs((List<JsonNode> list) -> list.get(0)))
                        .build());
    }

    @Test
    void testListenToOneSet() throws IOException {
        runIt(
                FlowWorkflowBuilder.workflow("listenToOneReviewSet")
                        .tasks(
                                listen("waitReview", toOne("org.acme.test.review"))
                                        .outputAs((Set<JsonNode> set) -> set.iterator().next()))
                        .build());
    }

    @Test
    void testListenToOneArray() throws IOException {
        runIt(
                FlowWorkflowBuilder.workflow("listenToOneReviewArray")
                        .tasks(
                                listen("waitReview", toOne("org.acme.test.review"))
                                        .outputAs((JsonNode[] array) -> array[0]))
                        .build());
    }

    @Test
    void testPrimitiveArray() {
        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Workflow workflow = FlowWorkflowBuilder.workflow("doubleArray")
                    .tasks(function(FuncEventFilterTest::doubleArray))
                    .build();
            WorkflowModelCollection col = app.modelFactory().createCollection();
            col.add(app.modelFactory().from(1));
            col.add(app.modelFactory().from(2));
            col.add(app.modelFactory().from(3));
            assertThat(
                    app.workflowDefinition(workflow)
                            .instance(col)
                            .start()
                            .join()
                            .as(int[].class)
                            .orElseThrow())
                    .isEqualTo(new int[] { 2, 4, 6 });
        }
    }

    private static int[] doubleArray(int[] input) {
        int[] output = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i] << 1;
        }
        return output;
    }

    private Workflow reviewEmitter() {
        return FlowWorkflowBuilder.workflow("emitReview")
                .tasks(emitJson("draftReady", "org.acme.test.review", Review.class))
                .build();
    }

    private void runIt(Workflow listen) throws IOException {
        Review review = new Review("Torrente", "espectacular", 5);

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {

            CompletableFuture<WorkflowModel> waiting = app.workflowDefinition(writeAndReadInMemory(listen)).instance(Map.of())
                    .start();

            app.workflowDefinition(reviewEmitter()).instance(review).start().join();

            assertThat(waiting.join().as(Review.class).orElseThrow()).isEqualTo(review);
        }
    }

    // --- Mock Service Methods ---
    NewsletterDraft writeDraft(NewsletterRequest req) {
        return new NewsletterDraft("Draft: " + req.topic(), "Initial body...");
    }

    NewsletterDraft editDraft(HumanReview review) {
        return new NewsletterDraft("Edited Draft", "Fixed based on: " + review.notes());
    }

    void sendEmail(NewsletterDraft draft) {
        // Simulates MailService.send
    }

    @Test
    void testAutomaticConversion() throws Exception {
        testConversionWorkflow(
                listen(
                        "waitHumanReview",
                        to().one(
                                consumed("org.acme.newsletter.review.done")
                                        .extensionByInstanceId("instanceid"))));
    }

    @Test
    void testCollectionConversion() throws Exception {
        testConversionWorkflow(
                listen(
                        to().one(
                                consumed("org.acme.newsletter.review.done")
                                        .extensionByInstanceId("instanceid")))
                        .outputAs((Collection<?> col) -> col.iterator().next()));
    }

    @Test
    void testNodeConversion() throws Exception {
        testConversionWorkflow(
                listen(
                        "waitHumanReview",
                        to().one(
                                consumed("org.acme.newsletter.review.done")
                                        .extensionByInstanceId("instanceid")))
                        .outputAs((ArrayNode col) -> col.get(0)));
    }

    private void testConversionWorkflow(ListenStep listen) throws Exception {
        Workflow workflow = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        function("draftAgent", this::writeDraft).exportAsTaskOutput(),
                        emitJson("draftReady", "org.acme.email.review.required", NewsletterDraft.class),
                        listen,
                        switchWhenOrElse(
                                h -> HumanReview.NEEDS_REVISION.equals(h.status()),
                                "humanEditorAgent",
                                "sendNewsletter",
                                HumanReview.class),
                        function("humanEditorAgent", this::editDraft)
                                .exportAsTaskOutput()
                                .then("draftReady"),
                        consume("sendNewsletter", this::sendEmail)
                                // Because we are in Jackson, the payload at this evaluation stage can be a
                                // Map.
                                // We simply check for the "status" field to know if it's the review payload.
                                .inputFrom(
                                        (Map<String, Object> payload,
                                                WorkflowContextData wfc,
                                                TaskContextData tfc) -> payload.containsKey("status") ? wfc.context()
                                                        : payload))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition definition = app.workflowDefinition(workflow);
            WorkflowInstance instance = definition.instance(new NewsletterRequest("Tech Stocks"));
            CompletableFuture<WorkflowModel> future = instance.start();

            await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> instance.status() == WorkflowStatus.WAITING);

            CloudEvent humanReviewEvent = CloudEventBuilder.v1()
                    .withId("event-123")
                    .withSource(URI.create("test:/human-editor"))
                    .withType("org.acme.newsletter.review.done")
                    .withExtension("instanceid", instance.id())
                    .withData(
                            "application/json",
                            "{\"status\":\"APPROVED\", \"notes\":\"Looks good\"}"
                                    .getBytes(StandardCharsets.UTF_8))
                    .build();

            EventPublisher publisher = app.eventPublishers().iterator().next();
            publisher.publish(humanReviewEvent).toCompletableFuture().join();

            future.join();

            assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
        }
    }

    record Review(String author, String text, int rating) {
    }

    record NewsletterRequest(String topic) {
    }

    record NewsletterDraft(String title, String body) {
    }

    record HumanReview(String status, String notes) {
        public static final String NEEDS_REVISION = "NEEDS_REVISION";
        public static final String APPROVED = "APPROVED";
    }
}
