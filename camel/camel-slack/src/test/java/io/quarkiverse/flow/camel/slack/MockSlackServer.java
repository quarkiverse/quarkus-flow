package io.quarkiverse.flow.camel.slack;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.HashMap;
import java.util.Map;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MockSlackServer implements QuarkusTestResourceLifecycleManager {

    private static ClientAndServer server;

    static MockServerClient client() {
        return new MockServerClient("localhost", server.getLocalPort());
    }

    @Override
    public Map<String, String> start() {
        server = ClientAndServer.startClientAndServer(0);

        client().when(
                request()
                        .withMethod("POST")
                        .withPath("/slack-webhook"))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withContentType(MediaType.TEXT_PLAIN)
                                .withBody("ok"));

        Map<String, String> props = new HashMap<>();
        props.put("slack.webhook.team1",
                "http://localhost:" + server.getLocalPort() + "/slack-webhook");
        return props;
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
