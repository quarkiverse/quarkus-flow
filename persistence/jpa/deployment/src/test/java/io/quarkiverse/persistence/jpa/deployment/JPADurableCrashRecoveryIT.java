package io.quarkiverse.persistence.jpa.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.persistence.test.AbstractDurableCrashRecoveryIT;
import io.quarkiverse.flow.persistence.test.durable.RecoveryEmitWorkflow;
import io.quarkiverse.flow.persistence.test.durable.RecoveryResource;
import io.quarkiverse.flow.persistence.test.durable.RecoveryWorkflow;
import io.quarkus.test.QuarkusDevModeTest;

@DisabledOnOs(OS.WINDOWS)
public class JPADurableCrashRecoveryIT extends AbstractDurableCrashRecoveryIT {

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RecoveryWorkflow.class, RecoveryEmitWorkflow.class, RecoveryResource.class)
                    .addAsResource("durable-recovery-application.properties", "application.properties"));

    @Override
    protected QuarkusDevModeTest getDevModeTest() {
        return devMode;
    }

    @Override
    protected void resetCounters() {
        RecoveryResource.reset();
    }
}
