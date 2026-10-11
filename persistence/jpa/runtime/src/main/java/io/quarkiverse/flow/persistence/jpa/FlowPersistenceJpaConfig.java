package io.quarkiverse.flow.persistence.jpa;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.persistence.jpa")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowPersistenceJpaConfig {

    /**
     * Name of the Hibernate ORM persistence unit (and matching named datasource) that Flow's own
     * JPA entities are routed to, if a datasource with that exact name is configured, otherwise
     * they fall back to the application's default persistence unit.
     */
    @WithDefault("flow-runtime")
    String persistenceUnitName();
}
