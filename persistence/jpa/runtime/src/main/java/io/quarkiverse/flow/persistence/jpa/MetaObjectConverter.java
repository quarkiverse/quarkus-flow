package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

@Converter(autoApply = false)
@ApplicationScoped
public class MetaObjectConverter implements AttributeConverter<Object, byte[]> {

    @Inject
    WorkflowBufferFactory factory;

    @Override
    public byte[] convertToDatabaseColumn(Object attribute) {
        return MarshallingUtils.writeObject(factory, attribute);
    }

    @Override
    public Object convertToEntityAttribute(byte[] dbData) {
        return MarshallingUtils.readObject(factory, dbData);
    }
}
