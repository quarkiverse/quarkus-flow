package org.acme.newsletter.agents;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.langchain4j.testing.evaluation.Evaluation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
public class DrafterAgentIT {

    private static final Logger LOG = LoggerFactory.getLogger(DrafterAgentIT.class.getName());

    @Inject
    DrafterAgent agent;
    @Inject
    NewsletterDraftEvaluationStrategy strategy;
    @Inject
    ObjectMapper mapper;

    @Test
    void testDrafterAgent() {
        final EvaluationReport<String> report = Evaluation.<String> builder()
                .withConcurrency(2)
                .withSamples("src/test/resources/samples/drafter-agent.yaml")
                .evaluate(params -> agent.draft(UUID.randomUUID().toString(), toDrafterJson(params)))
                .using(strategy)
                .run();

        assertThat(report.score()).as(() -> "AI output did not satisfy JSON contract or content checks.")
                .isGreaterThanOrEqualTo(80.0);
    }

    /**
     * Accepts either 1 JSON param or 6 separate params and returns a JSON string.
     */
    private String toDrafterJson(Parameters p) {
        if (p.size() == 1) {
            // Single parameter is already a JSON string
            return p.get(0).toString();
        }
        // Expecting: 0=marketMood, 1=topMovers, 2=macroData, 3=tone, 4=length, 5=notes
        ObjectNode payload = mapper.createObjectNode().put("marketMood", p.get(0).toString())
                .put("topMovers", p.get(1).toString()).put("macroData", p.get(2).toString())
                .put("tone", p.get(3).toString()).put("length", p.get(4).toString()).put("notes", p.get(5).toString());
        return payload.toString();
    }

    @Singleton
    public static class NewsletterDraftEvaluationStrategy implements EvaluationStrategy<String> {

        @Inject
        ObjectMapper mapper;

        @PostConstruct
        public void init() {
            mapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        }

        @Override
        public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
            try {
                LOG.info("Evaluating Drafter agent. Output is: \n {}", output);

                JsonNode node = mapper.readTree(output);

                if (node.isTextual()) {
                    output = node.asText();
                    node = mapper.readTree(output);
                }

                if (!node.isObject())
                    return EvaluationResult.fromBoolean(false);
                if (!node.has("draft") || !node.get("draft").isTextual())
                    return EvaluationResult.fromBoolean(false);

                String draft = node.get("draft").asText("");
                if (draft.isBlank())
                    return EvaluationResult.fromBoolean(false);

                String expected = sample.expectedOutput() == null ? "" : sample.expectedOutput();
                String[] lines = expected.split("\\R+");
                String mustContain = null;

                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.toUpperCase(Locale.ROOT).startsWith("MUST_CONTAIN:")) {
                        mustContain = trimmed.substring("MUST_CONTAIN:".length()).trim();
                    }
                }

                if (mustContain != null && !mustContain.isBlank()) {
                    String lower = draft.toLowerCase(Locale.ROOT);
                    for (String token : mustContain.split(",")) {
                        String t = token.trim().toLowerCase(Locale.ROOT);
                        if (!t.isEmpty() && !lower.contains(t)) {
                            return EvaluationResult.fromBoolean(false);
                        }
                    }
                }

                return EvaluationResult.fromBoolean(true);
            } catch (Exception e) {
                LOG.error("Failed to evaluate Agent response.", e);
                return EvaluationResult.fromBoolean(false);
            }
        }
    }
}
