package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static java.util.Map.entry;

import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(Call4PapersTest.WireMockTestResource.class)
public class Call4PapersTest {

    @Inject
    Call4PapersFlow flow;

    @BeforeAll
    static void setUp() {
    }

    @Test
    void should_execute_correctly() {
        var submission = new Call4PapersFlow.ProposalSubmission(
                "Reactive Workflows with Quarkus",
                "This paper explores reactive workflow patterns...",
                "Jane Developer");

        WorkflowModel workflowModel = flow.startInstance(submission).await().indefinitely();

        Map<String, Object> map = workflowModel.asMap().orElseThrow();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(map).contains(entry("status", "Notification sent"));
        });
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer mock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        @Override
        public Map<String, String> start() {
            mock.start();
            mock.addStubMapping(post("/notifications")
                    .withRequestBody(matchingJsonPath("$.title", equalTo("Reactive Workflows with Quarkus")))
                    .withRequestBody(matchingJsonPath("$.author", equalTo("Jane Developer")))
                    .withRequestBody(matchingJsonPath("$.score", equalTo("3")))
                    .withRequestBody(matchingJsonPath("$.accepted", equalTo("false")))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{ \"status\": \"Notification sent\" }"))
                    .build());
            return Map.of(
                    "notification.service.base-url", mock.baseUrl());
        }

        @Override
        public void stop() {
            mock.stop();
        }
    }
}
