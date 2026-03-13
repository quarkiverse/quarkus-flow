package io.quarkiverse.flow.devui;

import java.util.List;
import java.util.Objects;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkiverse.flow.config.FlowDevUIConfig;
import io.serverlessworkflow.impl.WorkflowStatus;

@ApplicationScoped
public class MVStoreWorkflowInstanceStore implements WorkflowInstanceStore {

    private static final Logger log = LoggerFactory.getLogger(MVStoreWorkflowInstanceStore.class);
    private static final String MAP_NAME = "flowInstances";

    @Inject
    FlowDevUIConfig config;

    private static final ObjectMapper objectMapper;

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(FlowInstance.class, new FlowInstanceSerializer());
        module.addDeserializer(FlowInstance.class, new FlowInstanceDeserializer());

        objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .registerModule(module);
    }

    private MVStore store;
    private MVMap<String, String> flowInstancesMap;

    @PostConstruct
    void init() {
        String path = config.mvstore().dbPath();
        try {
            store = MVStore.open(path);
            flowInstancesMap = store.openMap(MAP_NAME);
            log.debug("MVStore initialized at: {}", path);
        } catch (Exception e) {
            log.error("Failed to initialize MVStore at: {}", path, e);
            throw new RuntimeException("Failed to initialize MVStore", e);
        }
    }

    @PreDestroy
    void close() {
        if (store != null) {
            try {
                store.close();
                log.info("MVStore closed");
            } catch (Exception e) {
                log.warn("Error closing MVStore", e);
            }
        }
    }

    @Override
    public synchronized void saveOrUpdate(FlowInstance record) {
        if (record == null) {
            log.warn("Attempted to save null FlowInstance");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(record);
            flowInstancesMap.put(record.getInstanceId(), json);
            store.commit();
            log.debug("Saved workflow instance: {}", record.getInstanceId());
        } catch (JsonProcessingException e) {
            log.error("Error serializing workflow instance: {}", record.getInstanceId(), e);
            throw new RuntimeException("Failed to serialize FlowInstance", e);
        }
    }

    @Override
    public synchronized FlowInstance findByInstanceId(String instanceId) {
        String json = flowInstancesMap.get(instanceId);
        if (json == null) {
            return null;
        }
        return deserialize(json);
    }

    @Override
    public List<FlowInstance> findByStatus(WorkflowStatus status, int page, int size) {
        if (size <= 0 || page < 0) {
            return List.of();
        }
        return flowInstancesMap.values().stream()
                .map(this::deserialize)
                .filter(fi -> fi != null && status == fi.getStatus())
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    @Override
    public List<FlowInstance> listAll(int page, int size, Sort sort) {
        if (size <= 0 || page < 0) {
            return List.of();
        }
        return flowInstancesMap.values().stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .sorted(FlowInstanceComparators.forSort(sort))
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    private FlowInstance deserialize(String json) {
        try {
            return objectMapper.readValue(json, FlowInstance.class);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing workflow instance", e);
            return null;
        }
    }
}
