package io.quarkiverse.flow.camel.slack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class MockSlackServer implements QuarkusTestResourceLifecycleManager {

    private static MockWebServer server;

    static MockWebServer getServer() {
        return server;
    }

    @Override
    public Map<String, String> start() {
        server = new MockWebServer();

        server.setDispatcher(new Dispatcher() {
            @Override
            public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("/slack-webhook".equals(request.getPath())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setBody("ok")
                            .addHeader("Content-Type", "text/plain");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        Map<String, String> props = new HashMap<>();
        // This becomes: http://localhost:<port>/slack-webhook
        props.put("slack.webhook.team1", server.url("/slack-webhook").toString());
        return props;
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                server.shutdown();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
