package io.quarkiverse.flow.persistence.jpa.recorder;

import static io.quarkus.hibernate.orm.PersistenceUnit.PersistenceUnitLiteral;

import java.util.function.Function;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.persistence.EntityManager;

import io.quarkiverse.flow.persistence.jpa.CloudEventRepository;
import io.quarkiverse.flow.persistence.jpa.FlowPersistenceJpaConfig;
import io.quarkiverse.flow.persistence.jpa.HashMappingInfoRepository;
import io.quarkiverse.flow.persistence.jpa.JpaInstanceOperations;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceRepository;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashMappingCoordinator;

@Recorder
public class FlowPersistenceRecorder {

    private static final TypeLiteral<Event<HashMappingCoordinator>> EVENT_HASH_MAPPING_COORDINATOR_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public Function<SyntheticCreationalContext<JpaInstanceOperations>, JpaInstanceOperations> createJpaInstanceOperations(
            FlowPersistenceJpaConfig config, boolean useNamedPersistenceUnit) {
        return ctx -> {

            EntityManager em = useNamedPersistenceUnit
                    ? ctx.getInjectedReference(EntityManager.class, new PersistenceUnitLiteral(config.persistenceUnitName()))
                    : ctx.getInjectedReference(EntityManager.class);

            WorkflowInstanceRepository wir = ctx.getInjectedReference(WorkflowInstanceRepository.class);
            HashMappingInfoRepository hmir = ctx.getInjectedReference(HashMappingInfoRepository.class);
            CloudEventRepository cer = ctx.getInjectedReference(CloudEventRepository.class);
            WorkflowBufferFactory wbf = ctx.getInjectedReference(WorkflowBufferFactory.class);
            HashFactory hf = ctx.getInjectedReference(HashFactory.class);
            Event<HashMappingCoordinator> hmc = ctx.getInjectedReference(EVENT_HASH_MAPPING_COORDINATOR_TYPE_LITERAL);

            return new JpaInstanceOperations(
                    em,
                    wir,
                    hmir,
                    cer,
                    wbf,
                    hf,
                    hmc);
        };
    }
}
