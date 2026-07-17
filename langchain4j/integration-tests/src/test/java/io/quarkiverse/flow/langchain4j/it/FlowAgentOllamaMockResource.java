package io.quarkiverse.flow.langchain4j.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.ollamaResponse;

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
        wireMock = new WireMockServer(options().port(9696));
        wireMock.start();
        //WireMock.configureFor("0.0.0.0", wireMock.port());

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

        // Use 127.0.0.1 instead of localhost to avoid IPv4/IPv6 resolution issues in CI
        return Map.of("quarkus.langchain4j.ollama.base-url", "http://localhost:9696");
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }
}
