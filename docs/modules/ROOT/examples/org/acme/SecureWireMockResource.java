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
                .dynamicPort()
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
        int port = wireMockServer.port();
        String wiremockSecureUrl = "http://localhost:" + port;
        System.setProperty("wiremock.secure.port", String.valueOf(port));
        System.setProperty("wiremock.secure.url", wiremockSecureUrl);

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

        return Map.of(
                "wiremock.secure.port", String.valueOf(port),
                "wiremock.secure.url", wiremockSecureUrl);
    }

    @Override
    public void stop() {
        System.clearProperty("wiremock.secure.port");
        System.clearProperty("wiremock.secure.url");
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
