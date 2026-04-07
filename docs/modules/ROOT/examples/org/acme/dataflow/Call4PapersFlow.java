package org.acme.dataflow;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.http;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.output;

import java.net.URI;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

// tag::validate-proposal[]
@ApplicationScoped // <1>
public class Call4PapersFlow extends Flow { // <2>

    @ConfigProperty(name = "notification.service.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("call4papers")
                .tasks(
                        // tag::validate-and-score[]
                        // Step 1: Validate proposal with inputFrom transformation
                        function("validateProposal", (Proposal input) -> {
                            String proposalTitle = input.title();
                            if (proposalTitle == null || proposalTitle.isBlank()) {
                                throw new IllegalArgumentException("Title is required");
                            }
                            return input;
                        }, Proposal.class)
                                .inputFrom((ProposalSubmission submission) -> new Proposal(
                                        submission.title(),
                                        submission.proposal(), // Maps to abstractText
                                        submission.author()), ProposalSubmission.class),
                        // end::validate-proposal[]
                        // Step 2: Score proposal with exportAs transformation
                        function("scoreProposal", (Proposal input) -> { // <1>
                            Integer score = calculateScore(input.abstractText());
                            System.out.println("Score calculated having the result as: " + score);
                            return score;
                        }, Proposal.class)
                                .outputAs((Integer score) -> new ProposalScore(score, score >= 7)), // <2>
                        // end::validate-and-score[]
                        // tag::last-tasks[]
                        // Step 3: Prepare notification with exportAs using workflow context
                        function("prepareNotification", Function.identity(), ProposalScore.class) // <1>
                                .exportAs((object, workflowContext, taskContextData) -> { // <2>

                                    ProposalScore taskOutput = output(taskContextData, ProposalScore.class); // <3>

                                    ProposalSubmission submission = FuncDSL.input(workflowContext, // <4>
                                            ProposalSubmission.class);

                                    return new NotificationPayload( // <5>
                                            submission.title(),
                                            submission.author(),
                                            taskOutput.score(),
                                            taskOutput.accepted());
                                }),

                        // Step 4: Send notification via HTTP
                        http("sendNotification")
                                .POST()
                                .body("${ $context }") // <6>
                                .header("Content-Type", "application/json")
                                .uri(URI.create(baseUrl + "/notifications")))
                // end::last-tasks[]
                .build();
    }

    /**
     * Calculate a score for the proposal based on its abstract.
     * In a real implementation, this might use NLP, keyword analysis, etc.
     */
    private Integer calculateScore(String abstractText) {
        // Simple scoring: longer abstracts get higher scores
        int length = abstractText.length();
        if (length > 500)
            return 9;
        if (length > 300)
            return 7;
        if (length > 150)
            return 5;
        return 3;
    }

    // tag::data-types[]
    /**
     * External DTO received from the API
     */
    public record ProposalSubmission(String title, String proposal, String author) {
    }

    /**
     * Internal domain model used for processing
     */
    public record Proposal(String title, String abstractText, String author) {
    }

    /**
     * Scoring result persisted in workflow data
     */
    public record ProposalScore(long score, boolean accepted) {
    }

    /**
     * Final notification payload enriched with data from multiple sources
     */
    public record NotificationPayload(String title, String author, long score, boolean accepted) {
    }
    // end::data-types[]
}
