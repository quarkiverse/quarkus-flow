package org.acme;

// Static imports recommended for brevity:
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.call;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HttpWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("http-with-query-headers", "org.acme", "1.0")
                .tasks(
                        call("searchStarWarsCharacters",
                                http()
                                        .GET()
                                        // search value is taken from workflow input, jq expression is used
                                        .query("search", "${ .searchQuery }")
                                        .endpoint("http://localhost:8089/api/people")
                                        // Accept value is taken from workflow input, jq expression is used
                                        .header("Accept", "${ .acceptHeaderValue }")
                                        // export the results of the GET request as taskOutput
                                        .exportAsTaskOutput()))
                .build();
    }
}
