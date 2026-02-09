package io.quarkiverse.flow.durable.kube;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.serverlessworkflow.impl.WorkflowApplication;

@ApplicationScoped
public class InjectLeaseWorkflowApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(InjectLeaseWorkflowApplicationBuilderCustomizer.class);

    @Inject
    MemberLeaseCoordinator memberLeaseCoordinator;
    @Inject
    FlowDurableKubeSettings settings;

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        if (!settings.pool().member().leaseEnabled()) {
            return;
        }
        LOG.info("Flow: Binding Kubernetes Lease to Workflow Application ID");

        final String lease = memberLeaseCoordinator.awaitLease(Duration.ofSeconds(30));
        builder.withId(lease);

        LOG.info("Flow: Kubernetes Lease to Workflow Application ID is {}", lease);
    }
}
