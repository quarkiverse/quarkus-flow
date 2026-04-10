package org.acme;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class ExampleWorkflowsWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        // Start WireMock on the fixed port expected by the example workflows
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();

        // ---------------------------------------------------------
        // STUBS FOR HTTP WORKFLOW TESTS
        // ---------------------------------------------------------
        wireMockServer.stubFor(get(urlEqualTo("/api/people/%3Fsearch=luke"))
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
              "host": "localhost:8089",
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
            """;

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

        return Map.of(); // No properties to override
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}