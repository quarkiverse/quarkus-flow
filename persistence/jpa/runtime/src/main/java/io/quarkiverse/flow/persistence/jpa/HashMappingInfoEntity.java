package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import io.serverlessworkflow.impl.persistence.hashing.HashIndex;
import io.serverlessworkflow.impl.persistence.hashing.HashItem;

@Entity
@Table(indexes = { @Index(name = "hashKey_idx", columnList = "key"), @Index(name = "instance_idx", columnList = "instance") })
public class HashMappingInfoEntity {

    @Id
    private String id;

    @Column
    private String instance;

    @Column
    private HashItem key;

    @Column
    private byte[] data;

    protected HashMappingInfoEntity() {
    }

    public HashMappingInfoEntity(HashIndex id, String instanceId, HashItem key, byte[] data) {
        this.instance = instanceId;
        this.id = id.toString();
        this.key = key;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public HashItem getKey() {
        return key;
    }

    public void setKey(HashItem key) {
        this.key = key;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }
}
