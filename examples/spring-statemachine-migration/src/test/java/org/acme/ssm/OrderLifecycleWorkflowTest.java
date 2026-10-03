package org.acme.ssm;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.DefaultCloudEventMetadataBuilder;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.acme.ssm.domain.OrderRequest;
import org.acme.ssm.domain.OrderResult;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderLifecycleWorkflowTest {

    @Inject
    OrderLifecycleWorkflow workflow;

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    @DisplayName("approved_payment_then_shipment_reaches_delivered")
    void approved_payment_then_shipment_reaches_delivered() throws Exception {
        WorkflowInstance instance = workflow.instance(new OrderRequest("ORDER#1", "alice", 42.0));
        String id = instance.id();
        CompletableFuture<WorkflowModel> result = instance.start();

        InMemorySource<Message<String>> flowIn = connector.source("flow-in");
        flowIn.send(event(OrderLifecycleWorkflow.PAYMENT_RECEIVED, id,
                "{\"orderId\":\"ORDER#1\",\"approved\":true,\"reference\":\"PAY-1\"}"));
        awaitAwaitingShipment();
        flowIn.send(event(OrderLifecycleWorkflow.SHIPMENT_DISPATCHED, id,
                "{\"orderId\":\"ORDER#1\",\"carrier\":\"DHL\",\"trackingId\":\"TRK-9\"}"));

        OrderResult output = result.get(5, TimeUnit.SECONDS).as(OrderResult.class).orElseThrow();
        assertThat(output.finalState()).isEqualTo("DELIVERED");
        assertThat(output.orderId()).isEqualTo("ORDER#1");
    }

    @Test
    @DisplayName("rejected_payment_reaches_cancelled")
    void rejected_payment_reaches_cancelled() throws Exception {
        WorkflowInstance instance = workflow.instance(new OrderRequest("ORDER#2", "bob", 99.0));
        String id = instance.id();
        CompletableFuture<WorkflowModel> result = instance.start();

        InMemorySource<Message<String>> flowIn = connector.source("flow-in");
        flowIn.send(event(OrderLifecycleWorkflow.PAYMENT_RECEIVED, id,
                "{\"orderId\":\"ORDER#2\",\"approved\":false,\"reference\":\"PAY-2\"}"));

        OrderResult output = result.get(5, TimeUnit.SECONDS).as(OrderResult.class).orElseThrow();
        assertThat(output.finalState()).isEqualTo("CANCELLED");
        assertThat(output.orderId()).isEqualTo("ORDER#2");
    }

    private static void awaitAwaitingShipment() throws InterruptedException {
        // give the engine a moment to resume past the payment guard and reach awaitShipment
        TimeUnit.MILLISECONDS.sleep(500);
    }

    /** Builds a CloudEvent-carrying message the Flow engine can correlate to the instance. */
    private static Message<String> event(String type, String instanceId, String dataJson) {
        CloudEventMetadata<String> meta = new DefaultCloudEventMetadataBuilder<String>()
                .withId(UUID.randomUUID().toString())
                .withSpecVersion("1.0")
                .withSource(URI.create("test"))
                .withType(type)
                .withDataContentType("application/json")
                .withExtension("flowinstanceid", instanceId)
                .withData(dataJson)
                .build();
        return Message.of(dataJson).addMetadata(meta);
    }
}
