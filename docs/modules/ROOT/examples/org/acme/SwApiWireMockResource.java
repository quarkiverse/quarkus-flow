package org.acme;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class SwApiWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        // Start WireMock on the fixed port expected by the workflow
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();

        // Stub the specific endpoint
        wireMockServer.stubFor(get(urlEqualTo("/api/people/%3Fsearch=luke"))
                                       .withHeader("Accept", equalTo("application/json"))
                                       .willReturn(aResponse()
                                                           .withHeader("Content-Type", "application/json")
                                                           .withBody("""
                            {
                                "count": 1,
                                "results": [
                                    {
                                        "name": "Luke Skywalker Mock"
                                    }
                                ]
                            }
                            """)));

        return Map.of(); // No properties to override
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}