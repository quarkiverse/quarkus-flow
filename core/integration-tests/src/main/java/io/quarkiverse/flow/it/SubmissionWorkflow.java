package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.http;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SubmissionWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String reviewServiceUrl;

    @Override
    public Workflow descriptor() {
        return workflow("submissionWorkflow")
                .tasks(
                        http()
                                .uri(reviewServiceUrl + "/reviewers")
                                .header("Authorization", "${ \"Bearer \" + (.token) }")
                                .GET().outputAs("{ reviewers: . }"))
                .build();
    }
}
