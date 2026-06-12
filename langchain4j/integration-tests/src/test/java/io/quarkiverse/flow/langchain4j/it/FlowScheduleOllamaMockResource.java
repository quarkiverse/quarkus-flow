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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class FlowScheduleOllamaMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        // ConferenceReviewer score (Integer 0..10) — matched on text unique to the
        // proposal-scoring system prompt so it does not collide with the improver stub.
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("single integer score from 0 to 10"))
                .withRequestBody(containing("Quarkus Flow, Java and IA Orchestration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("8"))));

        // ConferenceReviewer improver — returns free-form, actionable feedback.
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("improve and refine talk proposals"))
                .withRequestBody(containing("Quarkus Flow, Java and IA Orchestration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Strengths: the title is concise and on-topic.\\n"
                                        + "Weaknesses: the abstract is too short and lacks technical depth.\\n"
                                        + "Suggestions: expand the description with concrete examples and a clearer "
                                        + "title such as 'Streamlining Java and AI Orchestration with Quarkus Flow'."))));

        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("You are an agent which use email tools to summary email inbox"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "You do not have emails!"))));

        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("You are an agent which use WhatsApp tools to summary WhatsApp messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "You do not have messages!"))));

        return Map.of("quarkus.langchain4j.ollama.base-url", wireMock.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }
}
