package io.quarkiverse.flow.deployment.test.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.config.ConfigManager;
import io.smallrye.common.annotation.Identifier;

public class SecretResolutionFromConfigFileTest {

    @Inject
    ConfigManager cm;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SecretEchoWorkflow.class)
                    .addAsResource(new StringAsset("mySecret.username=alice\n" +
                            "mySecret.password=s3cr3t!"),
                            "application.properties"));

    @Test
    public void secret_is_resolved_from_config_file() {
        var handle = Arc.container().instance(
                WorkflowDefinition.class,
                Identifier.Literal.of(SecretEchoWorkflow.class.getName()));
        assertTrue(handle.isAvailable());

        WorkflowDefinition def = handle.get();

        WorkflowModel model = def.instance(Map.of())
                .start()
                .join();

        String out = model.as(String.class).orElseThrow();
        assertEquals("s3cr3t!", out);
    }

    @Test
    public void config_manager_reads_dotted_secret_entries() {
        assertEquals("alice", cm.config("mySecret.username", String.class).orElse(null));
        assertEquals("s3cr3t!", cm.config("mySecret.password", String.class).orElse(null));

        var names = cm.names();
        boolean hasUser = false, hasPass = false;
        for (String n : names) {
            if ("mySecret.username".equals(n))
                hasUser = true;
            if ("mySecret.password".equals(n))
                hasPass = true;
        }
        assertTrue(hasUser && hasPass, "ConfigManager.names() should include our secret keys");
    }
}
