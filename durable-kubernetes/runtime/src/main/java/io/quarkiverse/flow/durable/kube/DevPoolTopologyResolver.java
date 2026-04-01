package io.quarkiverse.flow.durable.kube;

import static io.quarkiverse.flow.durable.kube.config.DevModeConfig.DEV_MODE_ENABLED_CONFIG;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.quarkus.arc.properties.IfBuildProperty;

@IfBuildProperty(name = DEV_MODE_ENABLED_CONFIG, stringValue = "true", enableIfMissing = true)
@ApplicationScoped
public class DevPoolTopologyResolver implements PoolTopologyResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DevPoolTopologyResolver.class.getName());

    @PostConstruct
    void init() {
        LOG.info("Flow: In devmode, initializing DevPoolTopologyResolver to not rely on non-existent Deployment object");
    }

    @Override
    public Optional<Integer> desiredReplicas() {
        return Optional.of(1);
    }

    @Override
    public List<OwnerReference> leaseOwnerReferences() {
        return List.of(); //ownerless, we don't have an actual deployment in devmode
    }
}
