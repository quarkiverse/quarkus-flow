package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator.X_FLOW_INSTANCE_ID;
import static io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator.X_FLOW_TASK_ID;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkiverse.flow.Flow;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.common.annotation.Identifier;

@QuarkusTest
@QuarkusTestResource(NamedHttpMetadataPropagationTest.WireMockTestResource.class)
public class NamedHttpMetadataPropagationTest {

    private static final String oneProfile = """
            [{ "profile": "orange-coder" }]
            """;
    private static final String threeProfiles = """
            [
                { "profile": "blue-coder" },
                { "profile": "red-coder"},
                { "profile": "green-coder"}
            ]
            """;

    @Inject
    @Identifier("flow:quarkus-flow")
    Flow quarkusFlowFlow;

    @Inject
    GetContributorsFlow getContributorsFlow;

    @BeforeEach
    void setup() {
        // Reset WireMock to clear previous test's stubs and requests
        WireMockTestResource.server.resetAll();
    }

    @Test
    void shouldPropagateCorrelationMetadata() throws IOException {
        // mock the download of OpenAPI from ServerlessWorkflow SDK
        String openAPI = readOpenAPIFromClasspath();

        WireMockTestResource.server.stubFor(get(urlEqualTo("/resources/schema.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAPI)));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/quarkiverse/quarkus-flow/contributors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(oneProfile)));

        WorkflowInstance instance = quarkusFlowFlow.instance(Map.of(
                "mockServerUrl", WireMockTestResource.server.baseUrl() + "/resources/schema.json"));

        // act
        instance.start().join();

        // assert
        List<LoggedRequest> requests = WireMockTestResource.server
                .findAll(getRequestedFor(urlPathEqualTo("/quarkiverse/quarkus-flow/contributors")));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(requests).hasSize(1);
            LoggedRequest correlationMetadata = requests.get(0);
            softly.assertThat(correlationMetadata.getHeader(X_FLOW_INSTANCE_ID)).isEqualTo(instance.id());
            softly.assertThat(correlationMetadata.getHeader(X_FLOW_TASK_ID))
                    .isEqualTo("do/0/getQuarkusFlowContributors");
        });
    }

    @Test
    void shouldPropagateX_Flow_Task_ID_PerTask() {
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/serverlessworkflow/sdk-java/contributors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(threeProfiles)));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/quarkusio/quarkus/contributors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(oneProfile)));

        WorkflowInstance instance = getContributorsFlow.instance();
        String instanceID = instance.id();

        // act
        instance.start().join();

        // assert
        List<LoggedRequest> sdkJavaRequests = WireMockTestResource.server.findAll(
                getRequestedFor(urlPathEqualTo("/serverlessworkflow/sdk-java/contributors")));
        List<LoggedRequest> quarkusRequests = WireMockTestResource.server.findAll(
                getRequestedFor(urlPathEqualTo("/quarkusio/quarkus/contributors")));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(sdkJavaRequests).hasSize(1);
            LoggedRequest serverlessWorkflowRequest = sdkJavaRequests.get(0);
            softly.assertThat(serverlessWorkflowRequest.getHeader(X_FLOW_INSTANCE_ID))
                    .isEqualTo(instanceID);
            softly.assertThat(serverlessWorkflowRequest.getHeader(X_FLOW_TASK_ID))
                    .isEqualTo("do/0/getSdkJavaContributors");

            softly.assertThat(quarkusRequests).hasSize(1);
            LoggedRequest quarkusRequest = quarkusRequests.get(0);
            softly.assertThat(quarkusRequest.getHeader(X_FLOW_INSTANCE_ID))
                    .isEqualTo(instanceID);
            softly.assertThat(quarkusRequest.getHeader(X_FLOW_TASK_ID))
                    .isEqualTo("do/1/getQuarkusContributors");
        });
    }

    @Test
    void shouldPropagateCorrelationMetadataUsingDifferentWorkflow() throws IOException {
        // mock the download of OpenAPI from ServerlessWorkflow SDK
        String openAPI = readOpenAPIFromClasspath();

        WireMockTestResource.server.stubFor(get(urlEqualTo("/resources/schema.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAPI)));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/quarkiverse/quarkus-flow/contributors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(threeProfiles)));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/serverlessworkflow/sdk-java/contributors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(oneProfile)));

        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/quarkusio/quarkus/contributors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(threeProfiles)));

        // first instance
        WorkflowInstance quarkusFlowInstance = quarkusFlowFlow.instance(Map.of(
                "mockServerUrl", WireMockTestResource.server.baseUrl() + "/resources/schema.json"));
        String quarkusFlowInstanceID = quarkusFlowInstance.id();

        // second instance
        WorkflowInstance sdkFlowInstance = getContributorsFlow.instance();
        String sdkFlowInstanceID = sdkFlowInstance.id();

        // act: start both synchronously
        quarkusFlowInstance.start().join();
        sdkFlowInstance.start().join();

        // assert
        List<LoggedRequest> quarkusFlowRequests = WireMockTestResource.server.findAll(
                getRequestedFor(urlPathEqualTo("/quarkiverse/quarkus-flow/contributors")));
        List<LoggedRequest> sdkJavaRequests = WireMockTestResource.server.findAll(
                getRequestedFor(urlPathEqualTo("/serverlessworkflow/sdk-java/contributors")));
        List<LoggedRequest> quarkusRequests = WireMockTestResource.server.findAll(
                getRequestedFor(urlPathEqualTo("/quarkusio/quarkus/contributors")));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(quarkusFlowRequests).hasSize(1);
            LoggedRequest quarkusFlowRequest = quarkusFlowRequests.get(0);
            softly.assertThat(quarkusFlowRequest.getHeader(X_FLOW_INSTANCE_ID)).isEqualTo(quarkusFlowInstanceID);
            softly.assertThat(quarkusFlowRequest.getHeader(X_FLOW_TASK_ID)).isEqualTo("do/0/getQuarkusFlowContributors");

            softly.assertThat(sdkJavaRequests).hasSize(1);
            LoggedRequest sdkJavaRequest = sdkJavaRequests.get(0);
            softly.assertThat(sdkJavaRequest.getHeader(X_FLOW_INSTANCE_ID)).isEqualTo(sdkFlowInstanceID);
            softly.assertThat(sdkJavaRequest.getHeader(X_FLOW_TASK_ID)).isEqualTo("do/0/getSdkJavaContributors");

            softly.assertThat(quarkusRequests).hasSize(1);
            LoggedRequest quarkusRequest = quarkusRequests.get(0);
            softly.assertThat(quarkusRequest.getHeader(X_FLOW_INSTANCE_ID)).isEqualTo(sdkFlowInstanceID);
            softly.assertThat(quarkusRequest.getHeader(X_FLOW_TASK_ID)).isEqualTo("do/1/getQuarkusContributors");
        });
    }

    public String readOpenAPIFromClasspath() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("openapi/http-correlation-metadata.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{mockServerPort}}", Integer.toString(WireMockTestResource.port));
        }
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;
        static int port;

        static {
            try {
                port = HttpPortUtils.generateRandomPort();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(port);
            server.start();
            return Map.of("named.http.metadata.propagation.url", server.baseUrl(),
                    "quarkus.flow.http.client.workflow.sdk-java-repository.name", "sdk-java-contributors");
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
            }
        }
    }
}
