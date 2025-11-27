package org.acme.examples.agentic;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.acme.agentic.InvestmentAnalystAgent;
import org.acme.agentic.InvestmentMemo;
import org.acme.agentic.InvestmentPrompt;
import org.junit.jupiter.api.Test;

/**
 * Simple test that drives the InvestmentMemoFlow through the REST endpoint.
 * <p>
 * We mock the InvestmentAnalystAgent so the test is fast and does not depend
 * on an external LLM or network.
 */
@QuarkusTest
public class InvestmentMemoResourceTest {

    @InjectMock
    InvestmentAnalystAgent analyst;

    @Test
    void shouldReturnInvestmentMemoForTicker() {
        when(analyst.analyse(anyString(), any(InvestmentPrompt.class)))
                .thenAnswer(invocation -> new InvestmentMemo(
                        "Solid long-term compounder with predictable cash flows.",
                        "BUY",
                        List.of("Valuation risk", "Regulation risk")));

        // Act + Assert: call the REST endpoint and verify the JSON payload
        given()
                .when()
                .get("/investments/CSU.TO")
                .then()
                .statusCode(200)
                .body("summary",
                        equalTo("Solid long-term compounder with predictable cash flows."))
                .body("stance", equalTo("BUY"))
                .body("keyRisks", hasItem("Valuation risk"))
                .body("keyRisks", hasItem("Regulation risk"));
    }
}
