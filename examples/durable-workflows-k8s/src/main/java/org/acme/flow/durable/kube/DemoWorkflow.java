package org.acme.flow.durable.kube;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

@ApplicationScoped
public class DemoWorkflow extends Flow {

    @ConfigProperty(name = "quarkus.http.port")
    Integer port;

    @Override
    public Workflow descriptor() {
        return workflow("lease-demo")
                .tasks(set(Map.of("initTimestamp", System.currentTimeMillis())),
                        get("http://localhost:" + port + "/delay/operation")
                                .outputAs((serviceOutput, wc, tc) -> {
                                    // Take this task input and merge it with the incoming string from our delayed HTTP service
                                    Map<String, Object> output = new HashMap<>(tc.input().asMap().orElse(Map.of()));
                                    output.put("httpResult", serviceOutput.get("response"));
                                    output.put("endTimestamp", System.currentTimeMillis());
                                    return output;
                                }, Map.class))
                .build();
    }
}
