package io.quarkiverse.flow.langchain4j.it.schedulable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.annotations.ScheduleOn;

public final class Call4PapersAgentic {

    private static final Logger log = LoggerFactory.getLogger(Call4PapersAgentic.class);

    private Call4PapersAgentic() {
    }

    public interface ConferenceReviewerPlanner {

        @Output
        static ProposalReview toProposalReview(@V("proposal") Proposal proposal, @V("considerations") String considerations,
                @V("proposalScore") Integer score) {
            log.info("Creating ProposalReview with proposal={}, considerations={}, score={}", proposal, considerations, score);
            return new ProposalReview(proposal, considerations, score);
        }

        @ScheduleOn(event = "proposal.submitted")
        @ParallelAgent(outputKey = "reviewResult", subAgents = { ProposalImproverAgent.class, ScoreJavaProposal.class })
        ProposalReview proposalReview(@V("proposal") Proposal proposal);
    }

    public interface ScoreJavaProposal {

        @Agent(name = "Java Talk Reviewer", outputKey = "proposalScore")
        @SystemMessage("""
                You are an experienced conference reviewer specializing in Java and cloud-native technologies.
                Your role is to evaluate talk proposals submitted to software conferences.
                Analyze the proposal critically and assign a score from 0 to 10 based on the following criteria:
                - Clarity and relevance of the title
                - Technical depth and accuracy
                - Audience value and learning outcomes
                - Originality and differentiation from common talks
                - Structure and readability of the abstract
                - Practicality, real-world applicability, and examples
                - Alignment between title, subject, and description
                - Grammar, tone, and professionalism

                You MUST respond with a single integer between 0 and 10. Do not include any explanation, punctuation, or extra text.
                """)
        @UserMessage("""
                Please review the following Java talk proposal:

                **ID**: {proposal.id}
                **Title**: {proposal.title}
                **Subject/Topic**: {proposal.subject}
                **Description/Abstract**: {proposal.description}

                Based on your evaluation criteria, respond with a single integer score from 0 to 10.
                """)
        Integer scoreProposal(@V("proposal") Proposal proposal);
    }

    public interface ProposalImproverAgent {

        @Agent(name = "Conference reviewer", outputKey = "considerations")
        @SystemMessage("""
                You are an experienced conference reviewer and technical speaker coach specializing in Java and cloud-native technologies.

                Your role is to improve and refine talk proposals submitted to software conferences.
                Analyze the proposal critically and provide constructive feedback focused on increasing the chances of acceptance.

                Evaluate the proposal using the following criteria:

                - Clarity and relevance of the title
                - Technical depth and accuracy
                - Audience value and learning outcomes
                - Originality and differentiation from common talks
                - Structure and readability of the abstract
                - Practicality, real-world applicability, and examples
                - Alignment between title, subject, and description
                - Grammar, tone, and professionalism

                Your response must:
                - Highlight strengths of the proposal
                - Point out weaknesses or unclear sections
                - Suggest concrete improvements
                - Recommend a stronger version of the title when appropriate
                - Suggest missing technical details or practical examples
                - Explain how the proposal can become more engaging for reviewers and attendees

                Keep the feedback objective, concise, and actionable (max. 15 lines).
                Assume the target audience consists of software engineers, architects, and Java developers.
                """)
        @UserMessage("""
                Please review the following Java talk proposal:

                **ID**: {proposal.id}
                **Title**: {proposal.title}
                **Subject/Topic**: {proposal.subject}
                **Description/Abstract**: {proposal.description}

                Provide improvements following the criteria outlined in your instructions.
                """)
        String improveProposal(@V("proposal") Proposal proposal);
    }

    public interface Agents {
        @Agent(outputKey = "reviewResult")
        ProposalReview proposalReview();

        @Agent(outputKey = "proposal")
        Proposal proposal();
    }

    public record ProposalReview(
            Proposal proposal,
            String considerations,
            Integer score) {
    }

    public record Proposal(Long id, String title, String subject, String description) {
    }

}
