package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

@Converter(autoApply = true)
@ApplicationScoped
public class WorkflowModelConverter implements AttributeConverter<WorkflowModel, byte[]> {

    @Inject
    WorkflowBufferFactory factory;

    @Override
    public byte[] convertToDatabaseColumn(WorkflowModel attribute) {
        return MarshallingUtils.writeModel(factory, attribute);
    }

    @Override
    public WorkflowModel convertToEntityAttribute(byte[] dbData) {
        return MarshallingUtils.readModel(factory, dbData);
    }
}
