package io.quarkiverse.flow.deployment.test.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

public class HttpFlowClientConfigTest {

    private static MockWebServer mockServer;

    @BeforeAll
    static void startMockServer() throws IOException {
        // Use a fixed port so we can wire it via overrideConfigKey
        mockServer = new MockWebServer();
        mockServer.start(1080);
    }

    @AfterAll
    static void stopMockServer() {
        mockServer.close();
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
    void http_client_uses_flow_http_config() throws InterruptedException {
        mockServer.enqueue(new MockResponse(200, Headers.of(Map.of("Content-Type", "application/json")), """
                { "message": "ok" }
                """));

        WorkflowDefinition definition = Arc.container()
                .instance(WorkflowDefinition.class, Identifier.Literal.of(HttpRestFlow.class.getName()))
                .get();

        WorkflowModel model = definition
                .instance(Map.of("message", "world"))
                .start()
                .join();

        String out = model.as(String.class).orElseThrow();
        assertEquals("{\"message\":\"ok\"}", out);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/echo", recordedRequest.getUrl().uri().getPath());

        assertEquals(1, mockServer.getRequestCount());
        assertEquals("flow-default", recordedRequest.getHeaders().get("X-Flow-Client"));
    }
}
