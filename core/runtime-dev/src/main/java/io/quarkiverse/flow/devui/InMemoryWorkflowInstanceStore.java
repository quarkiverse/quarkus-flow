package io.quarkiverse.flow.devui;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * In-memory implementation of WorkflowInstanceStore for dev mode.
 * Data is lost when the application restarts.
 */
@ApplicationScoped
public class InMemoryWorkflowInstanceStore implements WorkflowInstanceStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWorkflowInstanceStore.class);
    private final Map<String, FlowInstance> flowInstancesMap = new ConcurrentHashMap<>();

    @Override
    public synchronized void saveOrUpdate(FlowInstance record) {
        if (record == null) {
            log.warn("Attempted to save null or invalid WorkflowInstance");
            return;
        }
        flowInstancesMap.put(record.getInstanceId(), record);
    }

    @Override
    public synchronized FlowInstance findByInstanceId(String instanceId) {
        return flowInstancesMap.get(instanceId);
    }

    @Override
    public List<FlowInstance> findByStatus(WorkflowStatus status, int page, int size) {
        if (size <= 0 || page < 0) {
            return List.of();
        }
        return flowInstancesMap.values().stream()
                .filter(wi -> status == wi.getStatus())
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
                .sorted(FlowInstanceComparators.forSort(sort))
                .skip((long) page * size)
                .limit(size)
                .toList();
    }
}
