package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class HashMappingInfoRepository implements PanacheRepositoryBase<HashMappingInfoEntity, String> {

    public void deleteByInstance(String id) {
        delete("instance = ?1", id);
    }

}
