package org.acme.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PetstoreMockResource implements QuarkusTestResourceLifecycleManager {

    // IMPORTANT: Quarkus tests load resources from the test classpath (target/test-classes)
    private static final Path OPENAPI_TEST_PATH = Path.of("target/classes/openapi/petstore.json");

    private WireMockServer wireMock;
    private String originalOpenApi;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        configureFor("localhost", wireMock.port());

        // 1) findByStatus -> returns a list with a deterministic pet id
        stubFor(get(urlPathEqualTo("/api/v3/pet/findByStatus")).withQueryParam("status", matching(".*"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":123,\"name\":\"Mocked Pet\"}]")));

        // 2) getPetById -> returns deterministic pet details
        stubFor(get(urlPathEqualTo("/api/v3/pet/123")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"id\":123,\"name\":\"Mocked Pet\"}")));

        // Rewrite OpenAPI servers[0].url to the WireMock port
        rewriteOpenApi("http://localhost:" + wireMock.port() + "/api/v3/");

        return Map.of();
    }

    @Override
    public void stop() {
        // Best-effort restore so local workspace doesn't remain "dirty"
        if (originalOpenApi != null) {
            try {
                Files.writeString(OPENAPI_TEST_PATH, originalOpenApi, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    private void rewriteOpenApi(String newBaseUrl) {
        try {
            originalOpenApi = Files.readString(OPENAPI_TEST_PATH, StandardCharsets.UTF_8);

            // More robust: replace the first servers[0].url value, regardless of the previous URL
            String updated = originalOpenApi.replaceFirst(
                    "\"servers\"\\s*:\\s*\\[\\s*\\{\\s*\"url\"\\s*:\\s*\"[^\"]+\"",
                    "\"servers\": [{\"url\": \"" + newBaseUrl + "\"");

            Files.writeString(OPENAPI_TEST_PATH, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite test OpenAPI server url: " + OPENAPI_TEST_PATH, e);
        }
    }
}
