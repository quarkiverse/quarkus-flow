package org.acme.newsletter.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.acme.newsletter.domain.CriticInput;
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
public class CriticAgentTest {

    private static final Logger LOG = LoggerFactory.getLogger(CriticAgentTest.class);

    @Inject
    CriticAgent agent;

    @Inject
    CritiqueEvaluationStrategy strategy;

    @Test
    void critic_checks_json_and_constraints(
            @ScorerConfiguration(concurrency = 3) Scorer scorer,
            @SampleLocation("src/test/resources/samples/critic-agent.yaml") Samples<String> samples
    ) {
        EvaluationReport<String> report = scorer.evaluate(
                samples,
                (Parameters p) -> agent.critique(
                        UUID.randomUUID().toString(),
                        new CriticInput(p.get(0).toString(), p.get(1).toString(), p.get(2).toString())
                ),
                strategy
        );
        assertThat(report.score())
                .as("CriticAgent output didn’t satisfy JSON contract or constraint checks")
                .isGreaterThanOrEqualTo(80.0);
    }

    @Singleton
    public static class CritiqueEvaluationStrategy implements EvaluationStrategy<String> {

        @Inject
        ObjectMapper mapper;

        @PostConstruct
        void init() {
            mapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        }

        @Override
        public boolean evaluate(EvaluationSample<String> sample, String output) {
            try {
                LOG.info("CriticAgent output:\n{}", output);

                JsonNode node = parsePossiblyWrappedJson(output);
                if (!node.isObject()) return false;

                // Required fields
                JsonNode verdict = node.get("verdict");
                JsonNode reasons = node.get("reasons");
                if (verdict == null || !verdict.isTextual()) return false;
                String v = verdict.asText("").toLowerCase(Locale.ROOT);
                if (!("approve".equals(v) || "revise".equals(v))) return false;

                if (reasons == null || !reasons.isArray() || reasons.isEmpty()) return false;
                for (JsonNode r : reasons) {
                    if (!r.isTextual() || r.asText().isBlank()) return false;
                }

                // Optional scores sanity
                JsonNode scores = node.get("scores");
                if (scores != null && scores.isObject()) {
                    for (String k : new String[]{"clarity", "tone", "compliance", "factuality", "overall"}) {
                        JsonNode s = scores.get(k);
                        if (s != null && s.isNumber()) {
                            int val = s.asInt();
                            if (val < 0 || val > 100) return false;
                        }
                    }
                }

                // Parse expectations from sample
                String expected = sample.expectedOutput() == null ? "" : sample.expectedOutput();
                String[] lines = expected.split("\\R+");
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
                    if (!v.equals(expectVerdict)) return false;
                }

                if (expectRevised) {
                    JsonNode rd = node.get("revised_draft");
                    if (rd == null || !rd.isTextual() || rd.asText().isBlank()) return false;
                }

                if (!expectFind.isEmpty()) {
                    // Search tokens in reasons + revised_draft (if any)
                    StringBuilder haystack = new StringBuilder();
                    for (JsonNode r : reasons) haystack.append(' ').append(r.asText());
                    JsonNode rd = node.get("revised_draft");
                    if (rd != null && rd.isTextual()) haystack.append(' ').append(rd.asText());
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

        private JsonNode parsePossiblyWrappedJson(String raw) throws Exception {
            String s = raw.trim();
            if (s.startsWith("```")) {
                s = s.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```\\s*$", "");
            }
            JsonNode node = mapper.readTree(s);
            if (node.isTextual()) {
                node = mapper.readTree(node.asText());
            }
            return node;
        }
    }
}
