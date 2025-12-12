package io.quarkiverse.flow.camel.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import okhttp3.mockwebserver.RecordedRequest;

@QuarkusTest
@QuarkusTestResource(MockSlackServer.class)
class SlackNotificationWorkflowTest {

    @Inject
    SlackNotificationWorkflow workflow;

    @Test
    void shouldSendSlackNotificationFromWorkflow() throws InterruptedException, IOException {
        final String response = workflow.instance(Map.of("name", "Elisa"))
                .start()
                .join()
                .asText()
                .orElseThrow();

        assertThat(response).isNotEmpty();
        assertThat(response).isEqualTo("Hello Elisa");

        RecordedRequest recordedRequest = MockSlackServer.getServer().takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/slack-webhook");

        String body = recordedRequest.getBody().readUtf8();
        assertThat(body).contains("Hello Elisa");
        assertThat(body).contains("#alerts");
    }
}
