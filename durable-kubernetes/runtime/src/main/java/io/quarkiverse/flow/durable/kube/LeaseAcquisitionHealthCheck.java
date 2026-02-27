package io.quarkiverse.flow.durable.kube;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class LeaseAcquisitionHealthCheck implements HealthCheck {

    private static final String NAME = "Lease Acquisition";

    @Inject
    MemberLeaseCoordinator memberLeaseCoordinator;

    @Inject
    LeaseGroupConfig leaseConfig;

    @Inject
    PoolConfig poolConfig;

    @Inject
    KubeInfoStrategy kubeInfo;

    @ConfigProperty(name = "quarkus.flow.durable.kube.health.readiness.require-lease", defaultValue = "true")
    boolean requireLease;

    @Override
    public HealthCheckResponse call() {
        boolean enabled = leaseConfig.member().enabled();
        String lease = memberLeaseCoordinator.currentLeaseOrNull();

        HealthCheckResponseBuilder b = HealthCheckResponse.named(NAME)
                .withData("leaseEnabled", enabled)
                .withData("poolName", poolConfig.name())
                .withData("podName", safePodName())
                .withData("podNamespace", safePodNamespace());

        if (!enabled) {
            return b.up()
                    .withData("leaseAcquired", false)
                    .withData("reason", "Lease member is not enabled")
                    .build();
        }

        if (lease != null) {
            return b.withData("leaseAcquired", true)
                    .withData("leaseName", lease)
                    .up()
                    .build();
        }

        b.withData("leaseAcquired", false)
                .withData("reason", "No member lease currently held");

        return requireLease ? b.down().build() : b.up().build();
    }

    private String safePodName() {
        try {
            return kubeInfo.podName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String safePodNamespace() {
        try {
            return kubeInfo.namespace();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
