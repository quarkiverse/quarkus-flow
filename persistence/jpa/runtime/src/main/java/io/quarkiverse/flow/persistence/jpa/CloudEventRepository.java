package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class CloudEventRepository implements PanacheRepositoryBase<CloudEventEntity, String> {

    public Collection<CloudEventEntity> findByRegId(Collection<String> regIds) {
        return list("regId in ?1 and processedFlag != true", regIds);
    }

    public void setProcessed(Collection<String> ids) {
        update("processedFlag = true where id in ?1", ids);
    }

    public void clearProcessed() {
        update("processedFlag = false");
    }

    public void deleteByIds(Collection<String> values) {
        delete("id in ?1", values);
    }
}
