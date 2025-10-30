package io.quarkiverse.flow.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;

@Disabled("Disabled since on GitHub Actions Ollama stopped working")
@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class GenericAgenticTest {

    @Test
    public void testGenericAgentic() {
        final String result = given()
                .when()
                .body("Tell me in 1 line about Quarkus.")
                .post("/generic")
                .then()
                .statusCode(200)
                .extract().body().asString();
        Assertions.assertThat(result).isNotEmpty();
    }
}
