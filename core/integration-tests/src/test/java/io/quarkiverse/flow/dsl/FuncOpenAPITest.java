package io.quarkiverse.flow.dsl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.quarkiverse.flow.dsl.FlowDSL.openapi;

import java.net.URI;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(FuncOpenAPITest.WireMockTestResource.class)
public class FuncOpenAPITest {

    @Inject
    WorkflowApplication app;

    @BeforeEach
    void setup() {
        WireMockTestResource.server.resetAll();
    }

    @Test
    void test_openapi_document_with_non_jq_uri_string() {
        int port = WireMockTestResource.server.port();

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
                """
                .formatted(port);

        WireMockTestResource.server.stubFor(get(urlEqualTo("/v2/swagger.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockedSwaggerDoc)));

        WireMockTestResource.server.stubFor(get(urlEqualTo("/v2/pet/findByStatus?status=available"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"description\": \"OK\" }")));

        var w = FlowWorkflowBuilder.workflow("openapi-call-workflow")
                .tasks(
                        openapi()
                                .document(URI.create(WireMockTestResource.server.baseUrl() + "/v2/swagger.json"))
                                .operation("findPetsByStatus")
                                .parameters(Map.of("status", "available")))
                .build();

        WorkflowDefinition def = app.workflowDefinition(w);
        WorkflowInstance instance = def.instance(Map.of());
        WorkflowModel model = instance.start().join();

        SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(model).isNotNull();
                    softly.assertThat(model.asMap()).contains(Map.of("description", "OK"));
                });
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            server.start();
            return Map.of();
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
            }
        }
    }
}
