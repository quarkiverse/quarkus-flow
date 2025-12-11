package io.quarkiverse.flow.camel.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(MockSlackServer.class)
class SlackCamelConnectorTest {

    @Test
    void shouldPostMessageToMockSlackWebhook() {
        SlackCamelConnector<String, String> connector = new SlackCamelConnector<>("alerts", "slack.webhook.team1");
        String result = connector.apply("hello from quarkus-flow");
        MockSlackServer.client().verify(
                request()
                        .withMethod("POST")
                        .withPath("/slack-webhook"),
                exactly(1));
        // Camel Slack returns the same input to caller - it's a fire and forget pattern.
        assertEquals("hello from quarkus-flow", result);
    }

    @Test
    void connectorNameShouldBeSlack() {
        SlackCamelConnector<Void, Void> connector = new SlackCamelConnector<>("alerts", "slack.webhook.team1");

        assertEquals("slack", connector.connectorName());
    }
}
