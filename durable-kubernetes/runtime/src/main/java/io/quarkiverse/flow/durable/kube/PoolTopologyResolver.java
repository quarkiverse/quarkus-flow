package io.quarkiverse.flow.durable.kube;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.OwnerReference;

public interface PoolTopologyResolver {

    /** Desired replicas for the pool. */
    Optional<Integer> desiredReplicas();

    /** OwnerReferences to attach to created Leases */
    List<OwnerReference> leaseOwnerReferences();

}
