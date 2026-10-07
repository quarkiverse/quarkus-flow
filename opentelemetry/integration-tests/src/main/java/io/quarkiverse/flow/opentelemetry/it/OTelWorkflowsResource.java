package io.quarkiverse.flow.opentelemetry.it;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

@Path("otel-workflows")
public class OTelWorkflowsResource {

    @Inject
    @Identifier("otel:otel-set-task:1.0.0")
    Flow setTaskFlow;

    @Inject
    @Identifier("otel:otel-do-task:1.0.0")
    Flow doTaskFlow;

    @Inject
    @Identifier("otel:otel-fork-task:1.0.0")
    Flow forkTaskFlow;

    @Inject
    @Identifier("otel:otel-switch-task:1.0.0")
    Flow switchTaskFlow;

    @Inject
    @Identifier("otel:otel-for-task:1.0.0")
    Flow forTaskFlow;

    @Inject
    @Identifier("otel:otel-raise-task:1.0.0")
    Flow raiseTaskFlow;

    @Inject
    @Identifier("otel:otel-try-task:1.0.0")
    Flow tryTaskFlow;

    @Inject
    @Identifier("otel:otel-emit-task:1.0.0")
    Flow emitTaskFlow;

    @Inject
    @Identifier("otel:otel-wait-task:1.0.0")
    Flow waitTaskFlow;

    @Inject
    @Identifier("otel:otel-run-task:1.0.0")
    Flow runTaskFlow;

    @Inject
    @Identifier("otel:otel-listen-task:1.0.0")
    Flow listenTaskFlow;

    @Path("otel-set-task")
    @POST
    public Uni<Map<String, Object>> postSetTaskFlow(ObjectNode input) {
        return doCall(setTaskFlow, input);
    }

    @Path("otel-do-task")
    @POST
    public Uni<Map<String, Object>> postDoTaskFlow(ObjectNode input) {
        return doCall(doTaskFlow, input);
    }

    @Path("otel-fork-task")
    @POST
    public Uni<Map<String, Object>> postForkTaskFlow(ObjectNode input) {
        return doCall(forkTaskFlow, input);
    }

    @Path("otel-switch-task")
    @POST
    public Uni<Map<String, Object>> postSwitchTaskFlow(ObjectNode input) {
        return doCall(switchTaskFlow, input);
    }

    @Path("otel-for-task")
    @POST
    public Uni<Map<String, Object>> postForTaskFlow(ObjectNode input) {
        return doCall(forTaskFlow, input);
    }

    @Path("otel-raise-task")
    @POST
    public Uni<Map<String, Object>> postRaiseTaskFlow(ObjectNode input) {
        return doCall(raiseTaskFlow, input);
    }

    @Path("otel-try-task")
    @POST
    public Uni<Map<String, Object>> postTryTaskFlow(ObjectNode input) {
        return doCall(tryTaskFlow, input);
    }

    @Path("otel-emit-task")
    @POST
    public Uni<Map<String, Object>> postEmitTaskFlow(ObjectNode input) {
        return doCall(emitTaskFlow, input);
    }

    @Path("otel-wait-task")
    @POST
    public Uni<Map<String, Object>> postWaitTaskFlow(ObjectNode input) {
        return doCall(waitTaskFlow, input);
    }

    @Path("otel-run-task")
    @POST
    public Uni<Map<String, Object>> postRunTaskFlow(ObjectNode input) {
        return doCall(runTaskFlow, input);
    }

    @Inject
    @Identifier("otel:otel-http-simple:1.0.0")
    Flow httpSimpleFlow;

    @Inject
    @Identifier("otel:otel-http-fork:1.0.0")
    Flow httpForkFlow;

    @Path("otel-http-simple")
    @POST
    public Uni<Map<String, Object>> postHttpSimpleFlow(ObjectNode input) {
        return doCall(httpSimpleFlow, input);
    }

    @Path("otel-http-fork")
    @POST
    public Uni<Map<String, Object>> postHttpForkFlow(ObjectNode input) {
        return doCall(httpForkFlow, input);
    }

    /**
     * Starts an instance that waits for an event that is never sent, cancels it once it is WAITING and returns its id.
     */
    @Path("otel-listen-task/cancel")
    @POST
    public Map<String, Object> startAndCancelListenTaskFlow() throws InterruptedException {
        return startAndCancel(listenTaskFlow);
    }

    /**
     * Starts an instance, cancels it once it is WAITING in its first wait task and returns its id.
     */
    @Path("otel-wait-task/cancel")
    @POST
    public Map<String, Object> startAndCancelWaitTaskFlow() throws InterruptedException {
        return startAndCancel(waitTaskFlow);
    }

    private static Map<String, Object> startAndCancel(Flow flow) throws InterruptedException {
        var instance = flow.instance();
        instance.start();
        var deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (instance.status() != WorkflowStatus.WAITING) {
            if (Instant.now().isAfter(deadline)) {
                throw new InstanceNotWaitingException(instance);
            }
            Thread.sleep(10);
        }
        return Map.of("id", instance.id(), "cancelled", instance.cancel());
    }

    static class InstanceNotWaitingException extends RuntimeException {
        InstanceNotWaitingException(WorkflowInstance instance) {
            super("Instance %s never reached WAITING, status: %s".formatted(instance.id(), instance.status()));
        }
    }

    Uni<Map<String, Object>> doCall(Flow flow, ObjectNode input) {
        return flow.startInstance(input)
                .onItem()
                .transform(result -> result.asMap().orElse(new HashMap<>()));
    }
}
