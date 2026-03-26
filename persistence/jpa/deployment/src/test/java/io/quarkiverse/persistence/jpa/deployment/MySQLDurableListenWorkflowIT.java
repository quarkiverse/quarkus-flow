package io.quarkiverse.persistence.jpa.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.persistence.test.AbstractDurableListenWorkflowIT;
import io.quarkiverse.flow.persistence.test.durable.DurableResource;
import io.quarkiverse.flow.persistence.test.durable.EmitWorkflow;
import io.quarkiverse.flow.persistence.test.durable.ListenWorkflow;
import io.quarkus.test.QuarkusDevModeTest;

@DisabledOnOs(OS.WINDOWS)
public class MySQLDurableListenWorkflowIT extends AbstractDurableListenWorkflowIT {

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ListenWorkflow.class, EmitWorkflow.class, DurableResource.class)
                    .addAsResource("durable-application.properties", "application.properties"));

    @Override
    protected QuarkusDevModeTest getDevModeTest() {
        return devMode;
    }

}
