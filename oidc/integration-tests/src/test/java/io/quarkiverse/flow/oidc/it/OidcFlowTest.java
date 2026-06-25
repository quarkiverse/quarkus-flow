package io.quarkiverse.flow.oidc.it;

import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@ConnectWireMock
class OidcFlowTest {

    WireMock wireMock;

    @Test
    @DisplayName("Propagation scheme attaches subject token downstream")
    void propagation_scheme_attaches_subject_token_downstream() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .queryParam("subjectToken", "static-token-123")
                .get("/flow-oidc/propagation")
                .then()
                .statusCode(200)
                .body("authorized", is(true))
                .body("via", is("propagation"));
    }

    @Test
    @DisplayName("Client credentials scheme acquires and attaches token downstream")
    void client_credentials_scheme_acquires_and_attaches_token_downstream() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .get("/flow-oidc/client-credentials")
                .then()
                .statusCode(200)
                .body("authorized", is(true))
                .body("via", is("client-credentials"));
    }

    @Test
    @DisplayName("Client credentials scheme acquires and attaches token downstream (ASYNC)")
    void client_credentials_scheme_acquires_and_attaches_token_downstream_async() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .get("/flow-oidc/client-credentials?async=true")
                .then()
                .statusCode(202);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    wireMock.verifyThat(1, RequestPatternBuilder
                            .newRequestPattern().withUrl("/cc/resource"));
                });
    }

    @Test
    @TestSecurity(user = "cruz", roles = { "admin", "user" }, attributes = {
            @SecurityAttribute(key = "access_token", value = "dummy-RSax7ZTj0RRUHC170HBEcOWZvX7fTZFmy8TUSZqyc7PWRzcXATfjDL005mcMROmV")
    })
    @DisplayName("Get SecurityIdentity and attaches token to downstream (ASYNC)")
    void security_identity_scheme_acquires_and_attaches_token_downstream_async() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .get("/flow-oidc/protected?async=true")
                .then()
                .statusCode(202);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<LoggedRequest> loggedRequests = wireMock.find(RequestPatternBuilder
                            .newRequestPattern().withUrl("/propagation/resource").withHeader("Authorization",
                                    new EqualToPattern(
                                            "Bearer dummy-RSax7ZTj0RRUHC170HBEcOWZvX7fTZFmy8TUSZqyc7PWRzcXATfjDL005mcMROmV")));
                    Assertions.assertEquals(1, loggedRequests.size());
                });
    }
}
