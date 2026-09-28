package io.quarkiverse.flow.persistence.jpa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;
import io.serverlessworkflow.impl.persistence.hashing.HashFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashItem;

@Converter(autoApply = true)
@ApplicationScoped
public class HashItemConverter implements AttributeConverter<HashItem, byte[]> {

    @Inject
    WorkflowBufferFactory bufferFactory;

    @Inject
    HashFactory hashFactory;

    @Override
    public byte[] convertToDatabaseColumn(HashItem attribute) {
        if (attribute == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (WorkflowOutputBuffer buffer = bufferFactory.output(out)) {
            buffer.writeByte(attribute.id());
            attribute.writeKey(buffer);
        }
        return out.toByteArray();
    }

    @Override
    public HashItem convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        ByteArrayInputStream input = new ByteArrayInputStream(dbData);
        try (WorkflowInputBuffer buffer = bufferFactory.input(input)) {
            return hashFactory.fromBuffer(buffer.readByte(), buffer).orElseThrow();
        }
    }

}
