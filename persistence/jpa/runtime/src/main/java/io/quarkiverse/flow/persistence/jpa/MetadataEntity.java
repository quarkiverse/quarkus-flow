package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class MetadataEntity {

    @Embedded
    private JPAHashMappingInfo hashValue;

    @Column
    private byte[] rawValue;

    protected MetadataEntity() {
    }

    public JPAHashMappingInfo getHashValue() {
        return hashValue;
    }

    public void setHashValue(JPAHashMappingInfo hashValue) {
        this.hashValue = hashValue;
    }

    public byte[] getRawValue() {
        return rawValue;
    }

    public void setRawValue(byte[] rawValue) {
        this.rawValue = rawValue;
    }

    public abstract String getName();
}
