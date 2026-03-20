package io.quarkiverse.flow.deployment.test.recorders;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowApplication;

public class WorkflowApplicationIdTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .withConfigurationResource("application-named.properties");

    @Inject
    WorkflowApplication application;

    @Test
    void should_have_appid_not_empty() {
        assertThat(application.id()).isEqualTo("test-app");
    }

}
