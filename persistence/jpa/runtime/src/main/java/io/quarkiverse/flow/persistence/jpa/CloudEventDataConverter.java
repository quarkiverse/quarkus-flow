package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import io.cloudevents.CloudEventData;
import io.cloudevents.core.data.BytesCloudEventData;

@Converter(autoApply = true)
@ApplicationScoped
public class CloudEventDataConverter implements AttributeConverter<CloudEventData, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(CloudEventData attribute) {
        return attribute == null ? null : attribute.toBytes();
    }

    @Override
    public CloudEventData convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : BytesCloudEventData.wrap(dbData);
    }
}
