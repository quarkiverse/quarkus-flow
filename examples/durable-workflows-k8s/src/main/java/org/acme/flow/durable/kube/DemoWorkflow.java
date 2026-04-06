package org.acme.flow.durable.kube;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emit;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.produced;

@ApplicationScoped
public class DemoWorkflow extends Flow {

    @ConfigProperty(name = "org.acme.flow.durable.kube.service-host") // in tests, we are random
    String serviceHost;

    @Override
    public Workflow descriptor() {
        return workflow("lease-demo").tasks(function((payload) -> Map.of("initTimestamp", System.currentTimeMillis())),
                get(serviceHost + "/delay/operation").outputAs((serviceOutput, wc, tc) -> {
                    // Take this task input and merge it with the incoming string from our delayed HTTP service
                    Map<String, Object> output = new HashMap<>(tc.input().asMap().orElse(Map.of()));
                    Instant start = Instant.ofEpochMilli((long) output.get("initTimestamp")); // set on the first task
                    Instant end = Instant.now();
                    output.put("httpResult", serviceOutput.get("response"));
                    output.put("endTimestamp", end.toEpochMilli());
                    output.put("durationMillis", Duration.between(start, end).toMillis());
                    output.put("workflowInstanceID", wc.instanceData().id());
                    return output;
                }, Map.class), emit(produced("org.acme.flow.durable.kube.finished").jsonData(Map.class))).build();
    }
}
