package io.quarkiverse.flow.camel.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import okhttp3.mockwebserver.RecordedRequest;

@QuarkusTest
@QuarkusTestResource(MockSlackServer.class)
class SlackCamelConnectorTest {

    @Test
    void shouldPostMessageToMockSlackWebhook() throws InterruptedException, IOException {
        SlackCamelConnector<String, String> connector = new SlackCamelConnector<>("alerts", "slack.webhook.team1");

        String result = connector.apply("hello from quarkus-flow");

        // Camel returns the same response
        assertEquals("hello from quarkus-flow", result);

        RecordedRequest recordedRequest = MockSlackServer.getServer().takeRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/slack-webhook", recordedRequest.getPath());

        String body = recordedRequest.getBody().readUtf8();
        // Just make sure our text went through
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("hello from quarkus-flow");
    }

    @Test
    void connectorNameShouldBeSlack() {
        SlackCamelConnector<Void, Void> connector = new SlackCamelConnector<>("alerts", "slack.webhook.team1");

        assertEquals("slack", connector.connectorName());
    }
}
