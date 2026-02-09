package io.quarkiverse.flow.durable.kube;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observes {@link MemberLeaseEvent} to coordinate {@link io.serverlessworkflow.impl.WorkflowApplication} beans which workflow
 * instances shard identification.
 */
@ApplicationScoped
public class MemberLeaseCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(MemberLeaseCoordinator.class.getName());
    private final AtomicReference<String> current = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<String>> gate = new AtomicReference<>(new CompletableFuture<>());

    void onLeaseEvent(@Observes MemberLeaseEvent evt) {
        switch (evt.type()) {
            case ACQUIRED -> {
                current.set(evt.leaseName());
                gate.get().complete(evt.leaseName());
                LOG.info("Flow: Lease Member '{}' has been acquired", evt.leaseName());
            }
            case LOST, RELEASED -> {
                current.set(null);
                gate.set(new CompletableFuture<>());
                if (evt.type().equals(MemberLeaseEvent.Type.RELEASED)) {
                    LOG.debug("Lease '{}' has been released ", evt.leaseName());
                } else {
                    LOG.debug("Lease '{}' has been lost ", evt.leaseName());
                }

            }
        }
    }

    public String currentLeaseOrNull() {
        return current.get();
    }

    public String awaitLease(Duration timeout) {
        String now = current.get();
        if (now != null) {
            return now;
        }
        try {
            return gate.get().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Timeout waiting for member lease after " + timeout, e);
        }
    }
}
