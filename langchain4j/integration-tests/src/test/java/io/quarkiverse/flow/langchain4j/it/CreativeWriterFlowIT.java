package io.quarkiverse.flow.langchain4j.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.ollamaResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Tests the Flow-to-agent code path where a {@link CreativeWriterFlow} invokes a
 * {@link CreativeWriterAdapter} that calls a {@code @SequenceAgent}. This exercises the
 * scope serialization/deserialization between the parent workflow and the child agentic
 * workflow — the path where parameter values can be lost (Strings become null,
 * Integers become 0).
 *
 * <p>
 * The WireMock stubs intentionally match on the <b>actual parameter values</b> (e.g.
 * "dungeons and dragons", "nerds", "fantasy", "5") rather than generic keywords. If
 * parameters are lost during scope serialization the stubs will not match, WireMock
 * returns 404, and the test fails.
 *
 * @see <a href="https://github.com/quarkusio/quarkus-workshop-langchain4j/pull/376#issuecomment-5091522868">Bug report</a>
 */
@QuarkusTest
@QuarkusTestResource(value = CreativeWriterFlowIT.StrictParameterOllamaMock.class, restrictToAnnotatedClass = true)
public class CreativeWriterFlowIT {

    @Inject
    CreativeWriterFlow creativeWriterFlow;

    @Test
    @DisplayName("test_flow_calling_sequence_agent_preserves_string_and_integer_parameters")
    void test_flow_calling_sequence_agent_preserves_string_and_integer_parameters() {
        CreativeWriterRequest request = new CreativeWriterRequest("dungeons and dragons", "fantasy", "nerds", 5);

        WorkflowModel result = creativeWriterFlow.startInstance(request).await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.isNull()).isFalse();
        String story = result.asText().orElse(null);
        assertThat(story)
                .as("The sequence agent (CreativeWriterWithLimit -> AudienceEditor -> StyleEditor) should return a story")
                .isNotBlank();
    }

    /**
     * WireMock stubs that match on actual parameter values injected into LLM prompts.
     * Each agent's stub requires the corresponding input parameter value to be present
     * in the request body — proving it survived the Flow-to-agent scope serialization.
     */
    public static class StrictParameterOllamaMock implements QuarkusTestResourceLifecycleManager {

        private WireMockServer wireMock;

        @Override
        public Map<String, String> start() {
            wireMock = new WireMockServer(options()
                    .dynamicPort()
                    .notifier(new ConsoleNotifier(true)));
            wireMock.start();

            // CreativeWriterWithLimit: prompt uses {{topic}} and {{maxSentences}}
            // — match on both the topic String value AND the Integer value
            wireMock.stubFor(post(urlEqualTo("/api/chat"))
                    .withRequestBody(containing("creative"))
                    .withRequestBody(containing("dungeons and dragons"))
                    .withRequestBody(containing("5 sentences"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(ollamaResponse("A brave party ventured into the dungeon."))));

            // AudienceEditor: prompt uses {{audience}} — match on actual audience value
            wireMock.stubFor(post(urlEqualTo("/api/chat"))
                    .withRequestBody(containing("audience"))
                    .withRequestBody(containing("nerds"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(ollamaResponse(
                                    "A brave party of level-20 adventurers ventured into the dungeon."))));

            // StyleEditor: prompt uses {{style}} — match on actual style value
            wireMock.stubFor(post(urlEqualTo("/api/chat"))
                    .withRequestBody(containing("style"))
                    .withRequestBody(containing("fantasy"))
                    .atPriority(5)
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(ollamaResponse(
                                    "In the enchanted realm, a brave party of level-20 adventurers ventured into the dungeon."))));

            return Map.of("quarkus.langchain4j.ollama.base-url", wireMock.baseUrl());
        }

        @Override
        public void stop() {
            if (wireMock != null) {
                wireMock.stop();
            }
        }
    }
}
