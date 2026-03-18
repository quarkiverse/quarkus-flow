package io.quarkiverse.flow.it;

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

@ApplicationScoped
public class Call4PapersFlow extends Flow {

    @ConfigProperty(name = "wiremock.c4p.url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("call4papers")
                .tasks(
                        // Step 1: Validate proposal with inputFrom transformation
                        function("validateProposal", (Proposal input) -> {
                            System.out.println("Validating proposal: " + input.title());
                            return input;
                        }, Proposal.class)
                                .inputFrom((ProposalSubmission submission) -> new Proposal(
                                        submission.title(),
                                        submission.proposal(), // Maps to abstractText
                                        submission.author()), ProposalSubmission.class),

                        // Step 2: Score proposal with exportAs transformation
                        function("scoreProposal", (Proposal input) -> {
                            Integer score = calculateScore(input.abstractText());
                            System.out.println("Score calculated having the result as: " + score);
                            return score;
                        }, Proposal.class)
                                .outputAs((Integer score) -> new ProposalScore(score, score >= 7)),

                        // Step 3: Prepare notification with exportAs using workflow context
                        function("prepareNotification", Function.identity(), ProposalScore.class)
                                .exportAs((object, workflowContext, taskContextData) -> {

                                    // Use FuncDSL.output to get the task's output from taskContextData
                                    ProposalScore taskOutput = output(taskContextData, ProposalScore.class);

                                    // Access original workflow input to get title and author
                                    ProposalSubmission submission = FuncDSL.input(workflowContext,
                                            ProposalSubmission.class);

                                    // Create enriched payload combining original input with score
                                    return new NotificationPayload(
                                            submission.title(),
                                            submission.author(),
                                            taskOutput.score(),
                                            taskOutput.accepted());
                                }),

                        // Step 4: Send notification via HTTP
                        http("sendNotification")
                                .POST()
                                .body("${.}") // Uses the NotificationPayload from exportAs
                                .header("Content-Type", "application/json")
                                .uri(URI.create(baseUrl + "/notifications")))
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

    // Data types representing different stages of the workflow

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
}
