package org.acme;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ExampleWorkflowsWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    public int getPort() {
        return wireMockServer.port();
    }

    @Override
    public Map<String, String> start() {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig().dynamicPort();
        wireMockServer = new WireMockServer(config);
        wireMockServer.start();
        int port = wireMockServer.port();
        String wiremockUrl = "http://localhost:" + port;
        System.setProperty("wiremock.port", String.valueOf(port));
        System.setProperty("wiremock.url", wiremockUrl);

        // ---------------------------------------------------------
        // STUBS FOR HTTP WORKFLOW TESTS
        // ---------------------------------------------------------
        wireMockServer.stubFor(get(urlEqualTo("/api/people?search=luke"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "count": 1,
                                    "results": [
                                        {
                                            "name": "Luke Skywalker Mock"
                                        }
                                    ]
                                }
                                """)));

        // ---------------------------------------------------------
        // STUBS FOR OPENAPI WORKFLOW TESTS
        // ---------------------------------------------------------

        // 1. Stub the Swagger Document
        String mockedSwaggerDoc = """
                {
                  "swagger": "2.0",
                  "info": { "version": "1.0.0", "title": "Mock Petstore" },
                  "host": "localhost:%d",
                  "basePath": "/v2",
                  "schemes": [ "http" ],
                  "paths": {
                    "/pet/findByStatus": {
                      "get": {
                        "operationId": "findPetsByStatus",
                        "parameters": [
                          {
                            "name": "status",
                            "in": "query",
                            "required": true,
                            "type": "string"
                          }
                        ],
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  }
                }
                """.formatted(port);

        // Use any as the workflow first query with HEAD and the GET
        wireMockServer.stubFor(any(urlEqualTo("/v2/swagger.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockedSwaggerDoc)));

        // 2. Stub the actual Petstore Endpoint defined in the document above
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/pet/findByStatus"))
                .withQueryParam("status", equalTo("available"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        // The Petstore API returns a JSON array
                        .withBody("[{\"id\": 101, \"name\": \"Mocked Doggo\", \"status\": \"available\"}]")));

        // ---------------------------------------------------------
        // 3. STUB FOR LISTEN WORKFLOW (Event Wakeup)
        // ---------------------------------------------------------
        wireMockServer.stubFor(post(urlEqualTo("/start"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"started successfully\"}")));

        // ---------------------------------------------------------
        // 4. STUBS FOR CONDITIONAL WORKFLOW
        // ---------------------------------------------------------
        wireMockServer.stubFor(post(urlEqualTo("/approve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        wireMockServer.stubFor(post(urlEqualTo("/reject"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // ---------------------------------------------------------
        // 5. STUBS FOR ITERATION WORKFLOW
        // ---------------------------------------------------------
        wireMockServer.stubFor(post(urlEqualTo("/process-order"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"processed_orders_status\": \"success\"}")));

        // ---------------------------------------------------------
        // 7. STUBS FOR PARALLEL WORKFLOW
        // ---------------------------------------------------------
        wireMockServer.stubFor(post(urlEqualTo("/inventory-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        wireMockServer.stubFor(post(urlEqualTo("/credit-check"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        return Map.of(
                "wiremock.port", String.valueOf(port),
                "wiremock.url", wiremockUrl);
    }

    @Override
    public void stop() {
        System.clearProperty("wiremock.port");
        System.clearProperty("wiremock.url");
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
