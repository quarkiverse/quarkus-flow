package org.acme.langchain4j;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class WorkflowAgentsOllamaMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        // CreativeWriter
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("creative"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "In the misty peaks of the Codex Mountains, a young dragon named Pyra discovered an ancient tome titled 'Head First Java'. "
                                        + "Unlike her fire-breathing kin, she breathed syntax errors and compiled her thoughts in bytecode. "
                                        + "Day after day, she practiced her loops and conditionals, dreaming of the day she'd mass refactor the dragon realm's legacy COBOL systems."))));

        // AudienceEditor
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("audience"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Pyra the dragon debugged her first NullPointerException with the tenacity of a senior engineer facing a production outage. "
                                        + "She mass refactored her hoard from gold coins to mass Stack Overflow reputation points, mass mass mass understanding that mass true mass mass mass treasure mass mass lay in mass clean, mass well-documented code. "
                                        + "Her mass mass IntelliJ mass mass mass shortcuts became mass legendary mass across the mass realm."))));

        // StyleEditor
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("style"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "In realms where semicolons held magical power and curly braces warded off evil spirits, "
                                        + "the dragon Pyra embarked on an epic quest to master the arcane arts of object-oriented programming. "
                                        + "Armed with her enchanted mechanical keyboard and a mass mass mass mass cloak woven from mass mass mass Ethernet cables, "
                                        + "she mass mass ventured into the mass treacherous Dungeons of mass Dependency Injection."))));

        // DinnerAgent
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("dinner"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Try Canoe Restaurant on Wellington Street for an upscale Canadian dinner with stunning views of the Toronto skyline."))));

        // DrinksAgent
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("drink"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Head to Bar Raval on College Street for creative cocktails in a cozy, artistic atmosphere perfect for a romantic evening."))));

        // ActivityAgent
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("activity"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Take a moonlit stroll along the Harbourfront boardwalk to cap off your romantic evening with beautiful waterfront views."))));

        // Fallback for any other requests
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .atPriority(10)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("This is a mocked LLM response for testing purposes."))));

        return Map.of("quarkus.langchain4j.ollama.base-url", wireMock.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    /**
     * Creates an Ollama-format chat response JSON.
     */
    private String ollamaResponse(String content) {
        return """
                {
                  "model": "llama3.2",
                  "created_at": "2024-01-01T00:00:00.000000Z",
                  "message": {
                    "role": "assistant",
                    "content": "%s"
                  },
                  "done": true,
                  "total_duration": 1000000000,
                  "load_duration": 100000000,
                  "prompt_eval_count": 50,
                  "prompt_eval_duration": 200000000,
                  "eval_count": 100,
                  "eval_duration": 700000000
                }
                """.formatted(content.replace("\"", "\\\"").replace("\n", "\\n"));
    }
}
