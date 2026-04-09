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
        return FuncWorkflowBuilder.workflow("http-with-query-headers")
                .tasks(
                        call("searchStarWarsCharacters",
                                http()
                                    .GET()
                                    // query(...) is not supported yet, include the query in the URI
                                    // engine will replace  values in {} if they match the task input
                                    .endpoint("http://localhost:8089/api/people/?search={searchQuery}")
                                    // Accept value is taken from workflow input, jq expression is used
                                    .header("Accept", "${ .acceptHeaderValue }")
                                    // export the results of the GET request as taskOutput
                                    .exportAsTaskOutput())
                )
                .build();
    }
}
