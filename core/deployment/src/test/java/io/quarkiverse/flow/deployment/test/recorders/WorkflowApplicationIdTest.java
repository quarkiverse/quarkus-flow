package io.quarkiverse.flow.deployment.test.recorders;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.serverlessworkflow.impl.WorkflowApplication;

public class WorkflowApplicationIdTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .withConfigurationResource("application-named.properties");

    @Inject
    WorkflowApplication application;

    @Test
    void should_have_appid_equal_config() {
        assertThat(application.id()).isEqualTo("test-app");
    }

}
