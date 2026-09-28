package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import io.serverlessworkflow.impl.persistence.hashing.HashFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashIndex;

@Converter(autoApply = true)
@ApplicationScoped
public class HashIndexConverter implements AttributeConverter<HashIndex, byte[]> {

    @Inject
    HashFactory hashFactory;

    @Override
    public HashIndex convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : hashFactory.indexFromBytes(dbData);
    }

    @Override
    public byte[] convertToDatabaseColumn(HashIndex attribute) {
        return attribute == null ? null : attribute.toBytes();
    }
}
