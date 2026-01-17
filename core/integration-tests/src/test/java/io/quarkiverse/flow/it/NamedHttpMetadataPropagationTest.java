package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator.X_FLOW_INSTANCE_ID;
import static io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator.X_FLOW_TASK_ID;
import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.Flow;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.common.annotation.Identifier;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

@QuarkusTest
@TestProfile(NamedHttpMetadataPropagationTest.OnlyWorkflowNamedHttpClientProfile.class)
public class NamedHttpMetadataPropagationTest {

    private static MockWebServer mockServer;
    private static final Headers contentTypeJson = Headers.of("Content-Type", "application/json");
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

    static int randomPort;

    static {
        try {
            randomPort = generateRandomPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Was not possible to generate a random port", e);
        }
    }

    @BeforeEach
    void setup() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start(randomPort);
    }

    @AfterEach
    void shutdownServer() {
        mockServer.close();
    }

    @Inject
    @Identifier("flow.QuarkusFlow")
    Flow quarkusFlowFlow;

    @Inject
    GetContributorsFlow getContributorsFlow;

    @Test
    void shouldPropagateCorrelationMetadata() throws IOException, InterruptedException {
        // mock the download of OpenAPI from ServerlessWorkflow SDK
        String openAPI = readOpenAPIFromClasspath();

        mockServer.enqueue(new MockResponse(
                200,
                contentTypeJson,
                openAPI));

        mockServer.enqueue(new MockResponse(
                200,
                contentTypeJson,
                oneProfile));

        WorkflowInstance instance = quarkusFlowFlow.instance(Map.of(
                "mockServerUrl", "http://127.0.0.1:" + mockServer.getPort() + "/resources/schema.json"));

        // act
        instance.start().join();

        mockServer.takeRequest();

        RecordedRequest correlationMetadata = mockServer.takeRequest();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(correlationMetadata.getHeaders().get(X_FLOW_INSTANCE_ID)).isEqualTo(
                    instance.id());
            softly.assertThat(correlationMetadata.getHeaders().get(X_FLOW_TASK_ID))
                    .isEqualTo("do/0/getQuarkusFlowContributors");
        });
    }

    @Test
    void shouldPropagateX_Flow_Task_ID_PerTask() throws InterruptedException {

        mockServer.enqueue(new MockResponse(200, contentTypeJson, threeProfiles));
        mockServer.enqueue(new MockResponse(200, contentTypeJson, oneProfile));

        WorkflowInstance instance = getContributorsFlow.instance();

        String instanceID = instance.id();

        // act
        instance.start().join();

        // assert
        RecordedRequest serverlessWorkflowRequest = mockServer.takeRequest();
        RecordedRequest quarkusRequest = mockServer.takeRequest();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(serverlessWorkflowRequest.getHeaders().get(X_FLOW_INSTANCE_ID))
                    .isEqualTo(instanceID);
            softly.assertThat(serverlessWorkflowRequest.getHeaders().get(X_FLOW_TASK_ID))
                    .isEqualTo("do/0/getSdkJavaContributors");

            softly.assertThat(quarkusRequest.getHeaders().get(X_FLOW_INSTANCE_ID))
                    .isEqualTo(instanceID);
            softly.assertThat(quarkusRequest.getHeaders().get(X_FLOW_TASK_ID))
                    .isEqualTo("do/1/getQuarkusContributors");
        });
    }

    @Test
    void shouldPropagateCorrelationMetadataUsingDifferentWorkflow() throws IOException, InterruptedException {
        // mock the download of OpenAPI from ServerlessWorkflow SDK
        String openAPI = readOpenAPIFromClasspath();

        // getting OpenAPI document
        mockServer.enqueue(new MockResponse(200, contentTypeJson, openAPI));

        // quarkus-flow contributors
        mockServer.enqueue(new MockResponse(200, contentTypeJson, threeProfiles));

        // sdk-java contributors
        mockServer.enqueue(new MockResponse(200, contentTypeJson, oneProfile));

        // quarkus contributors
        mockServer.enqueue(new MockResponse(200, contentTypeJson, threeProfiles));

        // first instance
        var quarkusFlowInstance = quarkusFlowFlow.instance(Map.of(
                "mockServerUrl", "http://127.0.0.1:" + mockServer.getPort() + "/resources/schema.json"));
        String quarkusFlowInstanceID = quarkusFlowInstance.id();

        // second instance
        var sdkFlowInstance = getContributorsFlow.instance();
        String sdkFlowInstanceID = sdkFlowInstance.id();

        // act: start both synchronously
        quarkusFlowInstance.start().join();
        sdkFlowInstance.start().join();

        // ignore OpenAPI download
        mockServer.takeRequest();

        RecordedRequest quarkusFlowRequest = mockServer.takeRequest();

        RecordedRequest sdkJavaRequest = mockServer.takeRequest();
        RecordedRequest quarkusRequest = mockServer.takeRequest();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(quarkusFlowRequest.getHeaders().get(X_FLOW_INSTANCE_ID)).isEqualTo(quarkusFlowInstanceID);
            softly.assertThat(quarkusFlowRequest.getHeaders().get(X_FLOW_TASK_ID)).isEqualTo("do/0/getQuarkusFlowContributors");

            softly.assertThat(sdkJavaRequest.getHeaders().get(X_FLOW_INSTANCE_ID)).isEqualTo(sdkFlowInstanceID);
            softly.assertThat(sdkJavaRequest.getHeaders().get(X_FLOW_TASK_ID)).isEqualTo("do/0/getSdkJavaContributors");

            softly.assertThat(quarkusRequest.getHeaders().get(X_FLOW_INSTANCE_ID)).isEqualTo(sdkFlowInstanceID);
            softly.assertThat(quarkusRequest.getHeaders().get(X_FLOW_TASK_ID)).isEqualTo("do/1/getQuarkusContributors");
        });
    }

    public String readOpenAPIFromClasspath() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("openapi/http-correlation-metadata.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{mockServerPort}}", Integer.toString(mockServer.getPort()));
        }
    }

    @ApplicationScoped
    public static class GetContributorsFlow extends Flow {

        @Override
        public Workflow descriptor() {
            return workflow("sdk-java-repository")
                    .tasks(get("getSdkJavaContributors",
                            "http://localhost:" + mockServer.getPort() + "/serverlessworkflow/sdk-java/contributors"),
                            get("getQuarkusContributors",
                                    "http://localhost:" + mockServer.getPort() + "/quarkusio/quarkus/contributors"))
                    .build();
        }
    }

    public static class OnlyWorkflowNamedHttpClientProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.http.client.workflow.sdk-java-repository.name", "sdk-java-contributors");
        }
    }

    public static int generateRandomPort() throws IOException {
        return findRandomPort();
    }

    private static int findRandomPort() throws IOException {
        for (int i = 0; i < 100; i++) {
            int port = 1024 + (int) (Math.random() * 64512); // 1024 to 65535
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IOException("No available port found");
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
