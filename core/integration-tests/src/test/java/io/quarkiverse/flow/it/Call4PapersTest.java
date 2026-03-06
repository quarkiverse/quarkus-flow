package io.quarkiverse.flow.it;


import io.quarkiverse.flow.Flow;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Objects;
import java.util.function.Function;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.http;

@QuarkusTest
public class Call4PapersTest {

    @Inject
    Call4PapersFlow flow;

    @Test
    void should_execute_correctly() {
        var submission = new Call4PapersFlow.ProposalSubmission(
                "Reactive Workflows with Quarkus",
                "This paper explores reactive workflow patterns...",
                "Jane Developer"
        );
        WorkflowModel workflowModel = flow.startInstance(submission).await().indefinitely();

        Assertions.assertNotNull(workflowModel);
    }

    @ApplicationScoped
    public static class Call4PapersFlow extends Flow {

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

                            // Step 2: Score proposal with outputAs transformation
                            function("scoreProposal", (Proposal input) -> {
                                Integer score = calculateScore(input.abstractText());
                                System.out.println("Score calculated having the result as: " + score);
                                return score;
                            }, Proposal.class)
                                    .outputAs((Long score) -> {
                                                return new ProposalScore(score, score >= 7);
                                            }, Long.class),

                            // Step 3: Prepare notification with exportAs using workflow context
                            function("prepareNotification", proposalScore -> new ProposalScore(proposalScore.score, proposalScore.accepted), ProposalScore.class)
                                    .exportAs((ProposalScore proposalScore, WorkflowContextData workflowContext) -> {
                                        // Access original workflow input to get title and author
                                        WorkflowModel originalInput = workflowContext.instanceData().input();
                                        ProposalSubmission submission = originalInput.as(ProposalSubmission.class).orElseThrow();

                                        // Create enriched payload combining original input with score
                                        return new NotificationPayload(
                                                submission.title(),
                                                submission.author(),
                                                proposalScore.score(),
                                                proposalScore.accepted());
                                    }, ProposalScore.class).inputFrom(Function.identity(), ProposalScore.class),

                            // Step 4: Send notification via HTTP
                            http("sendNotification")
                                    .POST()
                                    .body("${.}") // Uses the NotificationPayload from exportAs
                                    .header("Content-Type", "application/json")
                                    .uri(URI.create("http://localhost:9999/notifications")))
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
}
