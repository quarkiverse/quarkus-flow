package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.serverlessworkflow.impl.persistence.hashing.HashIndex;

@ApplicationScoped
public class HashMappingInfoRepository implements PanacheRepositoryBase<HashMappingInfoEntity, HashIndex> {

    public void deleteByInstance(String id) {
        delete("instance = ?1", id);
    }

}
