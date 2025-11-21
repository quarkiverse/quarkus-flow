package io.quarkiverse.flow.deployment.test.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

public class HttpFlowClientConfigTest {

    private static ClientAndServer mockServer;

    @BeforeAll
    static void startMockServer() {
        // Use a fixed port so we can wire it via overrideConfigKey
        mockServer = ClientAndServer.startClientAndServer(1080);
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(HttpRestFlow.class))
            // Wire the endpoint used by HttpRestFlow
            .overrideConfigKey("org.acme.endpoint", "http://localhost:1080/echo")
            // Configure the Flow HTTP client: send a static header with this value
            .overrideConfigKey("quarkus.flow.http.client.static-headers", "X-Flow-Client=flow-default")
            .overrideConfigKey("quarkus.flow.http.client.user-agent", "HttpFlowClientConfigTest");

    @Test
    void http_client_uses_flow_http_config() {
        mockServer
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/echo"))
                .respond(
                        response()
                                .withHeader("Content-Type", "application/json")
                                .withStatusCode(200)
                                .withBody("{\"message\":\"ok\"}"));

        WorkflowDefinition definition = Arc.container()
                .instance(WorkflowDefinition.class, Identifier.Literal.of(HttpRestFlow.class.getName()))
                .get();

        WorkflowModel model = definition
                .instance(Map.of("message", "world"))
                .start()
                .join();

        String out = model.as(String.class).orElseThrow();
        assertEquals("{\"message\":\"ok\"}", out);

        HttpRequest[] recorded = mockServer.retrieveRecordedRequests(
                request()
                        .withMethod("POST")
                        .withPath("/echo"));

        assertEquals(1, recorded.length);
        assertEquals("flow-default", recorded[0].getFirstHeader("X-Flow-Client"));
    }
}
