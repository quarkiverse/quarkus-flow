package io.quarkiverse.flow.langchain4j.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;

public class WiremockOllamaUtils {

    private WiremockOllamaUtils() {
    }

    public static WireMockServer startOllamaMock() {
        WireMockServer wireMock = new WireMockServer(options()
                .dynamicPort()
                .notifier(new ConsoleNotifier(true)));
        wireMock.start();
        return wireMock;
    }

    public static void stubEmailSummaryAgent(WireMockServer wireMock) {
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("email tools"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("You do not have emails!"))));
    }

    public static void stubWhatsAppSummaryAgent(WireMockServer wireMock) {
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("WhatsApp tools"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("You do not have messages!"))));
    }

    public static void stubConferenceReviewerImprover(WireMockServer wireMock) {
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
    }

    public static void stubConferenceReviewerScore(WireMockServer wireMock) {
        wireMock.stubFor(post(urlEqualTo("/api/chat"))
                .withRequestBody(containing("single integer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaResponse("8"))));
    }

    /**
     * Creates an Ollama-format chat response JSON for plain text content.
     */
    public static String ollamaResponse(String content) {
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
    public static String ollamaResponseRaw(String jsonContent) {
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
