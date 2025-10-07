package org.acme.newsletter.agents;

import java.util.Locale;
import java.util.UUID;

import org.acme.newsletter.domain.NewsletterInput;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;


@QuarkusTest
@AiScorer
public class DrafterAgentTest {

    private static final Logger LOG = LoggerFactory.getLogger(DrafterAgentTest.class.getName());

    @Inject
    DrafterAgent agent;

    @Inject
    NewsletterDraftEvaluationStrategy strategy;

    @Test
    void testDrafterAgent(@ScorerConfiguration(concurrency = 2) Scorer scorer,
                          @SampleLocation("src/test/resources/samples/drafter-agent.yaml") Samples<String> samples) {
        final EvaluationReport<String> report = scorer.evaluate(
                samples,
                (Parameters p) -> {
                    final NewsletterInput input =
                            new NewsletterInput(
                                    p.get(0).toString(),
                                    p.get(1).toString(),
                                    p.get(2).toString(),
                                    p.get(3).toString(),
                                    p.get(4).toString(),
                                    p.get(5).toString());
                    return agent.draft(UUID.randomUUID().toString(), input);
                },
                strategy
        );
        assertThat(report.score()).as(() -> "AI output did not satisfy JSON contract or content checks.")
                .isGreaterThanOrEqualTo(80.0);
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
        public boolean evaluate(EvaluationSample<String> sample, String output) {
            try {
                LOG.info("Evaluating Drafter agent. Output is: \n {}", output);

                JsonNode node = mapper.readTree(output);

                if (node.isTextual()) {
                    output = node.asText();
                    node = mapper.readTree(output);
                }

                if (!node.isObject()) return false;
                if (!node.has("draft") || !node.get("draft").isTextual()) return false;

                String draft = node.get("draft").asText("");
                if (draft.isBlank()) return false;

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
                            return false;
                        }
                    }
                }

                return true;
            } catch (Exception e) {
                LOG.error("Failed to evaluate Agent response.", e);
                return false;
            }
        }


    }

}
