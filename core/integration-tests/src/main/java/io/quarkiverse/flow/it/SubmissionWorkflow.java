package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.http;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SubmissionWorkflow extends Flow {

    @ConfigProperty(name = "jwt.wiremock.url")
    String reviewServiceUrl;

    @Override
    public Workflow descriptor() {
        return workflow("submissionWorkflow")
                .tasks(
                        http()
                                .uri(reviewServiceUrl + "/reviewers")
                                .header("Authorization", "${ \"Bearer \" + (.token) }")
                                .get().outputAs("{ reviewers: . }"))
                .build();
    }
}
