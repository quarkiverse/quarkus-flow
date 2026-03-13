package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
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

        Assertions.assertNotNull(workflowModel);
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer mock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

        @Override
        public Map<String, String> start() {
            mock.start();
            mock.addStubMapping(post("/notifications")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{ \"status\": \"Notification sent\" }"))
                    .build());
            return Map.of(
                    "wiremock.c4p.url", mock.baseUrl());
        }

        @Override
        public void stop() {
            mock.stop();
        }
    }
}
