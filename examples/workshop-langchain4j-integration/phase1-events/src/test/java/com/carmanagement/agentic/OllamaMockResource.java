package com.carmanagement.agentic;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * WireMock resource for mocking Ollama responses in tests.
 * Prevents real LLM API calls during test execution.
 */
public class OllamaMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        // Mock CleaningAgent response
        wireMockServer.stubFor(post(urlPathMatching("/api/chat"))
                .withRequestBody(containing("cleaning"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "model": "llama3.2",
                                    "message": {
                                        "role": "assistant",
                                        "content": "REQUIRED"
                                    },
                                    "done": true
                                }
                                """)));

        // Mock CarConditionFeedbackAgent response
        wireMockServer.stubFor(post(urlPathMatching("/api/chat"))
                .withRequestBody(containing("condition"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "model": "llama3.2",
                                    "message": {
                                        "role": "assistant",
                                        "content": "Good condition, minor wear"
                                    },
                                    "done": true
                                }
                                """)));

        // Fallback for any other chat request
        wireMockServer.stubFor(post(urlPathMatching("/api/chat"))
                .atPriority(10)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "model": "llama3.2",
                                    "message": {
                                        "role": "assistant",
                                        "content": "NOT_REQUIRED"
                                    },
                                    "done": true
                                }
                                """)));

        return Map.of("quarkus.langchain4j.ollama.base-url", wireMockServer.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
