package io.quarkiverse.flow.langchain4j.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class FlowAgentOllamaMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMock;
    private final AtomicInteger scoreCallCount = new AtomicInteger(0);

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
                                        + "She refactored her hoard from gold coins to Stack Overflow reputation points, understanding that true treasure lay in clean, well-documented code. "
                                        + "Her IntelliJ shortcuts became legendary across the realm."))));

        // StyleEditor
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("style"))
                .atPriority(5)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "In realms where semicolons held magical power and curly braces warded off evil spirits, "
                                        + "the dragon Pyra embarked on an epic quest to master the arcane arts of object-oriented programming. "
                                        + "Armed with her enchanted mechanical keyboard and a cloak woven from Ethernet cables, "
                                        + "she ventured into the treacherous Dungeons of Dependency Injection."))));

        // FoodExpert
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("3 meals"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "Pasta Carbonara\\nGrilled Salmon\\nVegetable Stir Fry"))));

        // MovieExpert
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("3 movies"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse(
                                "The Princess Bride\\nWhen Harry Met Sally\\nLa La Land"))));

        // CategoryRouter
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("I have severe chest pain and difficulty breathing"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("MEDICAL"))));

        // StyleScorer
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("score"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformerParameter("scoreCallCount", scoreCallCount)
                        .withBody(ollamaResponse("0.85"))));

        return Map.of("quarkus.langchain4j.ollama.base-url", wireMock.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    /**
     * Creates an Ollama-format chat response JSON for plain text content.
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

    /**
     * Creates an Ollama-format chat response JSON for raw JSON content (arrays, objects).
     * The content is already JSON so we escape inner quotes properly.
     */
    private String ollamaResponseRaw(String jsonContent) {
        // Escape quotes inside the JSON content for embedding in the outer JSON string
        String escaped = jsonContent.replace("\"", "\\\"");
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
                """.formatted(escaped);
    }
}
