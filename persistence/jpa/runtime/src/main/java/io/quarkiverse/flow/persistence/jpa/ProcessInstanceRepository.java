package io.quarkiverse.flow.persistence.jpa;

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@ApplicationScoped
public class ProcessInstanceRepository {

    @Inject
    EntityManager em;

    public void persist(ProcessInstanceEntity entity) {
        em.persist(entity);
    }

    public void deleteById(ProcessInstanceKey key) {
        ProcessInstanceEntity entity = em.find(ProcessInstanceEntity.class, key);
        if (entity != null) {
            em.remove(entity);
        }
    }

    public Stream<ProcessInstanceEntity> stream(String query, Object... params) {
        TypedQuery<ProcessInstanceEntity> typedQuery = em.createQuery(query, ProcessInstanceEntity.class);
        for (int i = 0; i < params.length; i++) {
            typedQuery.setParameter(i + 1, params[i]);
        }
        return typedQuery.getResultStream();
    }

    public ProcessInstanceEntity findById(ProcessInstanceKey key) {
        return em.find(ProcessInstanceEntity.class, key);
    }

    public Optional<ProcessInstanceEntity> findByIdOptional(ProcessInstanceKey key) {
        return Optional.ofNullable(em.find(ProcessInstanceEntity.class, key));
    }
}