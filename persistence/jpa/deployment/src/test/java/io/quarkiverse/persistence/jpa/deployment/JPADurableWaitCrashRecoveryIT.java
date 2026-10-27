package io.quarkiverse.persistence.jpa.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.persistence.test.AbstractDurableWaitCrashRecoveryIT;
import io.quarkiverse.flow.persistence.test.durable.RecoveryWaitResource;
import io.quarkiverse.flow.persistence.test.durable.RecoveryWaitWorkflow;
import io.quarkiverse.flow.persistence.test.durable.WaitTaskCompletionListener;
import io.quarkus.test.QuarkusDevModeTest;

@DisabledOnOs(OS.WINDOWS)
public class JPADurableWaitCrashRecoveryIT extends AbstractDurableWaitCrashRecoveryIT {

    @RegisterExtension
    static QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RecoveryWaitWorkflow.class, RecoveryWaitResource.class, WaitTaskCompletionListener.class)
                    .addAsResource("durable-recovery-wait-application.properties", "application.properties"));

    @Override
    protected QuarkusDevModeTest getDevModeTest() {
        return devMode;
    }

    @Override
    protected void resetCounters() {
        RecoveryWaitResource.reset();
    }
}
