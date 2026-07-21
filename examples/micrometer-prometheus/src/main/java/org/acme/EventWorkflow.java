package org.acme;

import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;
import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Map;

@ApplicationScoped
public class EventWorkflow extends Flow {

    @Inject
    WorkflowWebSocket webSocket;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Workflow descriptor() {
        return workflow("wait-event").tasks(
                listen("waitApproval", toOne("org.acme.approval.decision.v1")),
                consume("processApproval", (Map approval) -> {
                    Log.info("Approval decision received: " + approval);
                    try {
                        String resultJson = objectMapper.writeValueAsString(approval);
                        webSocket.broadcast("""
                                { "type": "wait-event", "status": "completed", "result": %s }
                                """.formatted(resultJson));
                    } catch (Exception e) {
                        Log.error("Error broadcasting approval result", e);
                    }
                }, Map.class)).build();
    }
}
