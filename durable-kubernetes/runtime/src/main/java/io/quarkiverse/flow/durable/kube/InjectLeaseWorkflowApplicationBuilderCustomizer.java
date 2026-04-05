package io.quarkiverse.flow.durable.kube;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.durable.kube.config.LeaseGroupConfig;
import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.serverlessworkflow.impl.WorkflowApplication;

@ApplicationScoped
public class InjectLeaseWorkflowApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(InjectLeaseWorkflowApplicationBuilderCustomizer.class);

    @Inject
    MemberLeaseCoordinator memberLeaseCoordinator;

    @Inject
    LeaseGroupConfig leaseConfig;

    @Inject
    DevModeStrategy devModeStrategy;

    @Inject
    Event<LeaseStartupEvent> leaseStartupEvent;

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        if (!leaseConfig.member().enabled()) {
            return;
        }

        if (devModeStrategy.enabled()) {
            LOG.info("Flow: Dev Mode detected. Bypassing Kubernetes Lease acquisition for instant startup.");
            // Return early. The WorkflowApplication will fallback to a default/local UUID.
            return;
        }

        LOG.info("Flow: Binding Kubernetes Lease to Workflow Application ID");

        LOG.debug("Flow: Firing LeaseStartupEvent to initialize Kubernetes polling...");
        leaseStartupEvent.fire(new LeaseStartupEvent());

        final String lease = memberLeaseCoordinator.awaitLease(Duration.ofSeconds(30));
        builder.withId(lease);

        LOG.info("Flow: Kubernetes Lease to Workflow Application ID is {}", lease);
    }
}
