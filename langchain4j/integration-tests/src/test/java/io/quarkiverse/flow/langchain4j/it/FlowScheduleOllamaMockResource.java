package io.quarkiverse.flow.langchain4j.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.ollamaResponse;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class FlowScheduleOllamaMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(options()
                .port(9595)
                .notifier(new ConsoleNotifier(true))); // Enable verbose logging
        wireMock.start();
        //WireMock.configureFor("0.0.0.0", wireMock.port());

        // Catch-all stub for any unmatched requests - helps debugging
        // Register FIRST so it has LOWEST priority
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("5")))); // Default score if nothing else matches

        // Email summary agent - match on unique text
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("email tools"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "You do not have emails!"))));

        // WhatsApp summary agent - match on unique text
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("WhatsApp tools"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "You do not have messages!"))));

        // ConferenceReviewer improver — returns free-form, actionable feedback.
        // Match on "improve and refine" which is unique to the improver prompt
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("improve and refine"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Strengths: the title is concise and on-topic.\\n"
                                        + "Weaknesses: the abstract is too short and lacks technical depth.\\n"
                                        + "Suggestions: expand the description with concrete examples and a clearer "
                                        + "title such as 'Streamlining Java and AI Orchestration with Quarkus Flow'."))));

        // ConferenceReviewer score (Integer 0..10) — matched on text unique to the
        // proposal-scoring system prompt so it does not collide with the improver stub.
        // Match on "single integer" since that's unique to the scoring prompt
        // REGISTERED LAST = HIGHEST PRIORITY (WireMock matches in reverse order)
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("single integer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("8"))));

        // Use 127.0.0.1 instead of localhost to avoid IPv4/IPv6 resolution issues in CI
        return Map.of("quarkus.langchain4j.ollama.base-url", "http://localhost:9595");
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }
}
