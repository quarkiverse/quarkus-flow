package org.acme.newsletter.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.langchain4j.scorer.junit5.AiScorer;
import io.quarkiverse.langchain4j.scorer.junit5.SampleLocation;
import io.quarkiverse.langchain4j.scorer.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationReport;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationSample;
import io.quarkiverse.langchain4j.testing.scorer.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.scorer.Parameters;
import io.quarkiverse.langchain4j.testing.scorer.Samples;
import io.quarkiverse.langchain4j.testing.scorer.Scorer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.acme.newsletter.domain.CriticAgentReview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@AiScorer
public class CriticAgentIT {

    private static final Logger LOG = LoggerFactory.getLogger(CriticAgentIT.class);

    @Inject
    CriticAgent agent;
    @Inject
    CritiqueEvaluationStrategy strategy;
    @Inject
    ObjectMapper mapper;

    @Test
    void critic_checks_json_and_constraints(
            @ScorerConfiguration(concurrency = 2) Scorer scorer,
            @SampleLocation("src/test/resources/samples/critic-agent.yaml") Samples<CriticAgentReview> samples
    ) {
        // Agent now returns CriticAgentReview, so the report is parameterized with CriticAgentReview
        EvaluationReport<CriticAgentReview> report = scorer.evaluate(
                samples,
                (Parameters p) -> agent.critique(UUID.randomUUID().toString(), toCriticJson(p)),
                strategy
        );
        assertThat(report.score())
                .as("CriticAgent output didn’t satisfy JSON contract or constraint checks")
                .isGreaterThanOrEqualTo(80.0);
    }

    /**
     * Accepts either 1 JSON param or 3 separate params and returns a JSON string.
     */
    private String toCriticJson(Parameters p) {
        if (p.size() == 1) {
            // Single parameter is already a JSON string
            return p.get(0).toString();
        }
        // Expecting: 0=draft, 1=tone, 2=compliance
        ObjectNode payload = mapper.createObjectNode()
                .put("draft", p.get(0).toString())
                .put("tone", p.get(1).toString())
                .put("compliance", p.get(2).toString());
        return payload.toString();
    }

    @Singleton
    public static class CritiqueEvaluationStrategy implements EvaluationStrategy<CriticAgentReview> {

        private static String safeLower(String s) {
            return s == null ? "" : s.toLowerCase(Locale.ROOT);
        }

        private static String safe(String s) {
            return s == null ? "" : s;
        }

        @Override
        public boolean evaluate(EvaluationSample<CriticAgentReview> sample, CriticAgentReview output) {
            try {
                if (output == null) return false;

                // Basic contract checks
                String verdict = safeLower(output.getVerdict());
                if (!"approve".equals(verdict) && !"revise".equals(verdict)) return false;

                // Always require original draft echo
                if (output.getOriginalDraft() == null || output.getOriginalDraft().isBlank()) return false;

                // Reasons / suggestions presence
                boolean hasReason = output.getReasons() != null && output.getReasons().stream().anyMatch(r -> r != null && !r.isBlank());

                // For REVISE: must have at least one reason and a non-empty revised draft
                if ("revise".equals(verdict)) {
                    if (!hasReason) return false;
                    if (output.getRevisedDraft() == null || output.getRevisedDraft().isBlank()) return false;
                }

                // Scores sanity if present
                if (output.getScores() != null) {
                    Integer[] vals = {
                            output.getScores().getClarity(),
                            output.getScores().getTone(),
                            output.getScores().getCompliance(),
                            output.getScores().getFactuality(),
                            output.getScores().getOverall()
                    };
                    for (Integer v : vals) {
                        if (v != null && (v < 0 || v > 100)) return false;
                    }
                }

                String expectedText = String.valueOf(sample.expectedOutput());
                String[] lines = expectedText.split("\\R+");
                String expectVerdict = null;
                boolean expectRevised = false;
                List<String> expectFind = new ArrayList<>();

                for (String line : lines) {
                    String t = line.trim();
                    if (t.regionMatches(true, 0, "EXPECT_VERDICT:", 0, "EXPECT_VERDICT:".length())) {
                        expectVerdict = t.substring("EXPECT_VERDICT:".length()).trim().toLowerCase(Locale.ROOT);
                    } else if (t.equalsIgnoreCase("EXPECT_REVISED: true")) {
                        expectRevised = true;
                    } else if (t.regionMatches(true, 0, "EXPECT_FIND:", 0, "EXPECT_FIND:".length())) {
                        String list = t.substring("EXPECT_FIND:".length());
                        for (String token : list.split(",")) {
                            String norm = token.trim().toLowerCase(Locale.ROOT);
                            if (!norm.isEmpty()) expectFind.add(norm);
                        }
                    }
                }

                if (expectVerdict != null && !expectVerdict.isBlank()) {
                    if (!verdict.equals(expectVerdict)) return false;
                }

                if (expectRevised || "revise".equals(verdict)) {
                    if (output.getRevisedDraft() == null || output.getRevisedDraft().isBlank()) return false;
                }

                if (!expectFind.isEmpty()) {
                    // Search in reasons + suggestions + revised_draft
                    StringBuilder haystack = new StringBuilder();
                    if (output.getReasons() != null) {
                        for (String r : output.getReasons()) haystack.append(' ').append(safe(r));
                    }
                    if (output.getSuggestions() != null) {
                        for (String s2 : output.getSuggestions()) haystack.append(' ').append(safe(s2));
                    }
                    haystack.append(' ').append(safe(output.getRevisedDraft()));

                    String lower = haystack.toString().toLowerCase(Locale.ROOT);
                    for (String token : expectFind) {
                        if (!lower.contains(token)) return false;
                    }
                }

                return true;
            } catch (Exception e) {
                LOG.error("Failed to evaluate CriticAgent response.", e);
                return false;
            }
        }
    }
}
