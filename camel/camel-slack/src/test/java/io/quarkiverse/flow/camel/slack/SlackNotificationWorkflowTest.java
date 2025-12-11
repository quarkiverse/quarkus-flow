package io.quarkiverse.flow.camel.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.exactly;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end test: starts a workflow that uses the Slack connector and verifies
 * that the Slack webhook is called on our MockServer.
 */
@QuarkusTest
@QuarkusTestResource(MockSlackServer.class)
class SlackNotificationWorkflowTest {

    @Inject
    SlackNotificationWorkflow workflow;

    @Test
    void shouldSendSlackNotificationFromWorkflow() {
        final String response = workflow.instance(Map.of("name", "Elisa"))
                .start()
                .join()
                .asText()
                .orElseThrow();
        assertThat(response).isNotEmpty();

        MockSlackServer.client().verify(
                request()
                        .withMethod("POST")
                        .withPath("/slack-webhook")
                        .withBody(json("""
                                {
                                  "text": "Hello Elisa",
                                  "channel": "#alerts"
                                }
                                """.trim())),
                exactly(1));
    }
}
