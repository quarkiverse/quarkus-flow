package org.acme;

// Static imports recommended for brevity:
import static io.quarkiverse.flow.dsl.FlowDSL.*;
import static io.quarkiverse.flow.dsl.FlowDSL.call;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class HttpWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("http-with-query-headers", "org.acme", "1.0")
                .tasks(
                        call("searchStarWarsCharacters",
                                http()
                                        .get()
                                        // search value is taken from workflow input, jq expression is used
                                        .query("search", "${ .searchQuery }")
                                        .endpoint(wiremockUrl + "/api/people")
                                        // Accept value is taken from workflow input, jq expression is used
                                        .header("Accept", "${ .acceptHeaderValue }")
                                        // export the results of the GET request as taskOutput
                                        .exportAsTaskOutput()))
                .build();
    }
}
