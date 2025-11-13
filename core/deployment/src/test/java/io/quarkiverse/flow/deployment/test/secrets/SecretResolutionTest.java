package io.quarkiverse.flow.deployment.test.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

public class SecretResolutionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SecretEchoWorkflow.class)
                    .addClass(DumbCredentialsProvider.class))
            // select our @Named("dumb") provider globally for secrets
            .overrideConfigKey("quarkus.flow.secrets.credentials-provider-name", "dumb");

    @Test
    public void secret_is_resolved_from_credentials_provider() {
        var handle = Arc.container().instance(
                WorkflowDefinition.class,
                Identifier.Literal.of(SecretEchoWorkflow.class.getName()));
        assertTrue(handle.isAvailable());

        WorkflowDefinition def = handle.get();

        WorkflowModel model = def.instance(Map.of()) // no input needed
                .start()
                .join();

        String out = model.as(String.class).orElseThrow();
        assertEquals("s3cr3t!", out);
    }
}
