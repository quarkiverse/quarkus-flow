package org.acme.flow.durable.kube;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import io.serverlessworkflow.api.types.EventFilter;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.TypeEventRegistration;
import io.serverlessworkflow.impl.events.TypeEventRegistrationBuilder;

@ApplicationScoped
public class NativeWorkflowEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeWorkflowEventListener.class);

    @Inject
    WorkflowApplication workflowApplication;

    @Inject
    WorkflowEventSocket socket;

    @Inject
    ObjectMapper mapper;

    /**
     * Wires up the InMemory event consumer when Quarkus starts.
     */
    @SuppressWarnings("unchecked")
    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("Registering InMemory Serverless Workflow event listener...");

        EventConsumer<TypeEventRegistration, TypeEventRegistrationBuilder> eventConsumer = (EventConsumer<TypeEventRegistration, TypeEventRegistrationBuilder>) workflowApplication
                .eventConsumer();

        EventProperties properties = new EventProperties();
        properties.setType("org.acme.flow.durable.kube.finished");

        EventFilter filter = new EventFilter();
        filter.setWith(properties);

        TypeEventRegistrationBuilder builder = eventConsumer.listen(filter, workflowApplication);

        eventConsumer.register(builder, this::handleCloudEvent);
    }

    /**
     * Callback triggered whenever a workflow emits the finished event.
     */
    private void handleCloudEvent(CloudEvent cloudEvent) {
        LOGGER.info("Caught in-memory workflow event! Broadcasting to UI...");

        try {
            if (cloudEvent.getData() != null) {
                byte[] rawData = cloudEvent.getData().toBytes();
                Map<String, Object> eventPayload = mapper.readValue(rawData, Map.class);

                // The workflowInstanceID is already in the payload!
                // We just add the backend completion timestamp for the UI.
                eventPayload.put("completedAt", System.currentTimeMillis());

                socket.broadcast(eventPayload);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse and broadcast CloudEvent", e);
        }
    }
}