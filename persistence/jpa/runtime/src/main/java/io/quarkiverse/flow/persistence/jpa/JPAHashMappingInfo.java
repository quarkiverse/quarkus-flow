package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.serverlessworkflow.impl.persistence.hashing.HashIndex;
import io.serverlessworkflow.impl.persistence.hashing.HashItem;

@Embeddable
public class JPAHashMappingInfo {

    @Column
    private HashItem hashKey;
    @Column
    private HashIndex hashIndex;

    public JPAHashMappingInfo() {
    }

    public JPAHashMappingInfo(HashItem hashKey, HashIndex hashIndex) {
        this.hashKey = hashKey;
        this.hashIndex = hashIndex;
    }

    public HashItem getHashItem() {
        return hashKey;
    }

    public HashIndex getHashIndex() {
        return hashIndex;
    }

}
