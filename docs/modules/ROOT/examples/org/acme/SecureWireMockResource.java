package org.acme;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class SecureWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    private static final String FAKE_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ3b3JrZmxvdy1ydW50aW1lLWlkIiwiZXhwIjoxOTk5OTk5OTk5fQ.dummy-signature";

    @Override
    public Map<String, String> start() {
        // 1. Enable WireMock's built-in verbose logging using ConsoleNotifier
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
                .port(8090)
                .notifier(new ConsoleNotifier(true));

        wireMockServer = new WireMockServer(config);

        // 2. Add a custom Request Listener for highly visible output in your test logs
        wireMockServer.addMockServiceRequestListener((request, response) -> {
            System.out.println("\n====== 🚦 WIREMOCK INTERCEPTED REQUEST 🚦 ======");
            System.out.println("Method : " + request.getMethod());
            System.out.println("URL    : " + request.getUrl());
            if (request.containsHeader("Authorization")) {
                System.out.println("Auth   : " + request.header("Authorization").firstValue());
            }
            System.out.println("Status : " + response.getStatus());
            System.out.println("================================================\n");
        });

        wireMockServer.start();

        // 3. Mock Keycloak returning the valid JWT format
        wireMockServer.stubFor(post(urlMatching("/realms/fake-authority.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "%s",
                                    "token_type": "Bearer",
                                    "expires_in": 3600
                                }
                                """.formatted(FAKE_JWT))));

        // 4. Mock Petstore requiring the exact valid JWT
        wireMockServer.stubFor(get(urlEqualTo("/v2/pet?petId=99"))
                .withHeader("Authorization", equalTo("Bearer " + FAKE_JWT))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 99,
                                    "name": "Secure Doggo"
                                }
                                """)));

        return Map.of();
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
