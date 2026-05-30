package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class CloudEventRepository {

    @Inject
    EntityManager em;

    public void persist(CloudEventEntity entity) {
        em.persist(entity);
    }

    public Collection<CloudEventEntity> findByRegId(Collection<String> regIds) {
        return em.createQuery("SELECT c FROM CloudEventEntity c WHERE c.regId IN :regIds", CloudEventEntity.class)
                .setParameter("regIds", regIds)
                .getResultList();
    }

    public void setProcessed(Collection<String> ids) {
        em.createQuery("UPDATE CloudEventEntity c SET c.processedFlag = true WHERE c.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }

    public void clearProcessed() {
        em.createQuery("UPDATE CloudEventEntity c SET c.processedFlag = false")
                .executeUpdate();
    }

    public void deleteByIds(Collection<String> values) {
        em.createQuery("DELETE FROM CloudEventEntity c WHERE c.id IN :ids")
                .setParameter("ids", values)
                .executeUpdate();
    }
}
