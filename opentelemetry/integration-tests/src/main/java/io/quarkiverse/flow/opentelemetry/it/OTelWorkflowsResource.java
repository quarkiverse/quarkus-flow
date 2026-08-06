package io.quarkiverse.flow.opentelemetry.it;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.flow.Flow;
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

    Uni<Map<String, Object>> doCall(Flow flow, ObjectNode input) {
        return flow.startInstance(input)
                .onItem()
                .transform(result -> result.asMap().orElse(new HashMap<>()));
    }
}
