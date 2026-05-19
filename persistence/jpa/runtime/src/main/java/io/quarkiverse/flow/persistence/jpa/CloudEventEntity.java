package io.quarkiverse.flow.persistence.jpa;

import java.net.URI;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.ColumnDefault;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;

@Entity
public class CloudEventEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String regId;

    @Column
    @ColumnDefault("false")
    private boolean processedFlag;

    @Column(nullable = false)
    private SpecVersion version;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private URI source;

    @Column
    private String subject;

    @Column
    private OffsetDateTime time;

    @Column
    private URI dataSchema;

    @Column
    private String dataContentType;

    @Column
    private CloudEventData data;

    @Column
    private byte[] extensions;

    public CloudEventEntity() {
    }

    public CloudEventEntity(String regId, CloudEvent event, byte[] extensions) {
        this.id = event.getId();
        this.regId = regId;
        this.version = event.getSpecVersion();
        this.type = event.getType();
        this.source = event.getSource();
        this.subject = event.getSubject();
        this.time = event.getTime();
        this.dataSchema = event.getDataSchema();
        this.dataContentType = event.getDataContentType();
        this.data = event.getData();
        this.extensions = extensions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegId() {
        return regId;
    }

    public void setRegId(String regId) {
        this.regId = regId;
    }

    public boolean isProcessedFlag() {
        return processedFlag;
    }

    public void setProcessedFlag(boolean processedFlag) {
        this.processedFlag = processedFlag;
    }

    public SpecVersion getVersion() {
        return version;
    }

    public void setVersion(SpecVersion version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public URI getSource() {
        return source;
    }

    public void setSource(URI source) {
        this.source = source;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public void setTime(OffsetDateTime time) {
        this.time = time;
    }

    public URI getDataSchema() {
        return dataSchema;
    }

    public void setDataSchema(URI dataSchema) {
        this.dataSchema = dataSchema;
    }

    public String getDataContentType() {
        return dataContentType;
    }

    public void setDataContentType(String dataContentType) {
        this.dataContentType = dataContentType;
    }

    public CloudEventData getData() {
        return data;
    }

    public void setData(CloudEventData data) {
        this.data = data;
    }

    public byte[] getExtensions() {
        return extensions;
    }

    public void setExtensions(byte[] extensions) {
        this.extensions = extensions;
    }

}
