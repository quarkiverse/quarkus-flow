package io.quarkiverse.flow.deployment.test;

import static io.serverlessworkflow.fluent.spec.WorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.Flowable;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;

public class FlowWorkflowDuplicatedTest {

    @RegisterExtension
    static QuarkusUnitTest quarkusUnitTest = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(
                    A.class, B.class))
            .assertException(throwable -> {
                IllegalStateException e = (IllegalStateException) throwable;
                Assertions.assertTrue(e.getMessage().contains(
                        "Multiple Workflows with the same identifier (namespace, name, and version) are not allowed: org.acme:same:0.0.1"));
            });

    @Test
    void assertTrue() {
        // assertion lives on assertException() consumer
    }

    @ApplicationScoped
    static class A implements Flowable {
        @Override
        public Workflow descriptor() {
            return workflow("same")
                    .tasks(b -> b.set("hello"))
                    .build();
        }
    }

    @ApplicationScoped
    static class B implements Flowable {
        @Override
        public Workflow descriptor() {
            return workflow("same")
                    .tasks(b -> b.set("hello"))
                    .build();
        }
    }
}
