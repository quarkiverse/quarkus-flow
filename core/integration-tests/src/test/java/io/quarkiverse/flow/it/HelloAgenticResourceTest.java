package io.quarkiverse.flow.it;

import static io.restassured.RestAssured.given;

import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HelloAgenticResourceTest {

    @Inject
    Config config;

    @BeforeAll
    void configureRestAssuredTimeouts() {
        // Read the same timeout Quarkus uses for Ollama. Accepts ISO-8601 (e.g., PT2M) or "120s".
        int ms = getOllamaTimeoutMillis();
        RestAssured.config = RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", ms) // connect timeout
                        .setParam("http.socket.timeout", ms) // read timeout
                        .setParam("http.connection-manager.timeout", (long) ms)); // pool wait
    }

    private int getOllamaTimeoutMillis() {
        Optional<Duration> dur = config.getOptionalValue("quarkus.langchain4j.ollama.timeout", Duration.class);
        if (dur.isPresent())
            return (int) Math.min(Integer.MAX_VALUE, dur.get().toMillis());

        String raw = config.getOptionalValue("quarkus.langchain4j.ollama.timeout", String.class).orElse("120s");
        try {
            if (raw.endsWith("ms"))
                return Integer.parseInt(raw.substring(0, raw.length() - 2));
            if (raw.endsWith("s"))
                return Math.toIntExact(Long.parseLong(raw.substring(0, raw.length() - 1)) * 1000L);
            if (raw.endsWith("m"))
                return Math.toIntExact(Long.parseLong(raw.substring(0, raw.length() - 1)) * 60_000L);
            return (int) Duration.parse(raw).toMillis();
        } catch (Exception ignored) {
            return 120_000; // final safety net
        }
    }

    @Test
    public void testHelloEndpoint() {
        final String result = given()
                .when()
                .body("Hello World!")
                .post("/hello")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertThat(result).containsIgnoringCase("Hello World");
    }

    @Test
    public void testHelloEndpoint_EmptyBody() {
        final String result = given()
                .when()
                .body("")
                .post("/hello")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertThat(result).containsIgnoringCase("is empty");
    }
}
