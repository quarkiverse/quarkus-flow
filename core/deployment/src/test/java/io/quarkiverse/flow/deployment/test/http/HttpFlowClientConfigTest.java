package io.quarkiverse.flow.deployment.test.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator.X_FLOW_INSTANCE_ID;
import static io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator.X_FLOW_TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

public class HttpFlowClientConfigTest {

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startMockServer() {
        wireMockServer = new WireMockServer(options().port(1080));
        wireMockServer.start();

        wireMockServer.stubFor(post(urlEqualTo("/echo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"message\": \"ok\" }")));
    }

    @AfterAll
    static void stopMockServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(HttpRestFlow.class))
            // Wire the endpoint used by HttpRestFlow
            .overrideConfigKey("org.acme.endpoint", "http://localhost:1080/echo")
            // Configure the Flow HTTP client: send a static header with this value
            .overrideConfigKey("quarkus.flow.http.client.static-headers", "X-Flow-Client=flow-default")
            .overrideConfigKey("quarkus.flow.http.client.user-agent", "HttpFlowClientConfigTest");

    @Test
    void http_client_uses_flow_http_config() {
        WorkflowDefinition definition = Arc.container()
                .instance(WorkflowDefinition.class, Identifier.Literal.of(HttpRestFlow.class.getName()))
                .get();

        WorkflowInstance instance = definition
                .instance(Map.of("message", "world"));

        WorkflowModel model = instance.start().join();

        String out = model.as(String.class).orElseThrow();
        assertThat(out).isEqualTo("{\"message\":\"ok\"}");

        // Verify the request was made with the correct method and path
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/echo"))
                .withHeader("X-Flow-Client", equalTo("flow-default")));

        // Get the logged request to verify additional headers
        LoggedRequest recordedRequest = wireMockServer.findAll(postRequestedFor(urlEqualTo("/echo"))).get(0);

        String xFlowInstanceId = recordedRequest.getHeader(X_FLOW_INSTANCE_ID);
        assertThat(xFlowInstanceId).isEqualTo(instance.id());

        String xFlowTaskId = recordedRequest.getHeader(X_FLOW_TASK_ID);
        assertThat(xFlowTaskId).isNotNull();
    }
}
