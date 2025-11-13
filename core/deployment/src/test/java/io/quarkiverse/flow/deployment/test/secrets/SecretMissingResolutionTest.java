package io.quarkiverse.flow.deployment.test.secrets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.Flow;
import io.quarkus.arc.Arc;
import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

/**
 * Verifies that when a CredentialsProvider returns no credentials for a secret,
 * the SDK surfaces the proper error (our bridge returns an empty map and the
 * SDK handles the error path).
 */
public class SecretMissingResolutionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MissingSecretFlow.class)
                    .addClass(EmptyCredentialsProvider.class));

    @Test
    public void missing_secret_raises_sdk_error() {
        var handle = Arc.container().instance(
                WorkflowDefinition.class,
                Identifier.Literal.of(MissingSecretFlow.class.getName()));
        assertTrue(handle.isAvailable(), "WorkflowDefinition should be available");

        Exception ex = assertThrows(Exception.class, () -> {
            handle.get().instance(Map.of()).start().join();
        });

        var msg = String.valueOf(ex.getMessage()).toLowerCase();
        assertTrue(msg.contains("https://serverlessworkflow.io/spec/1.0.0/errors/authorization,"),
                "Expected error message to mention secret resolution");
    }

    @ApplicationScoped
    public static class EmptyCredentialsProvider implements CredentialsProvider {
        @Override
        public Map<String, String> getCredentials(String name) {
            return Map.of();
        }
    }

    @ApplicationScoped
    public static class MissingSecretFlow extends Flow {
        @Override
        public Workflow descriptor() {
            return WorkflowBuilder.workflow()
                    .use(u -> u.secrets("myMissingSecret"))
                    .tasks(t -> t.set("${ $secret.myMissingSecret.key }"))
                    .build();
        }
    }
}
