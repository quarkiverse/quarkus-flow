package io.quarkiverse.flow.devui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.FlowInstance;
import io.quarkiverse.flow.internal.WorkflowInstanceStore;

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
        if (record == null || record.instanceId() == null) {
            log.warn("Attempted to save null or invalid WorkflowInstance");
            return;
        }
        flowInstancesMap.put(record.instanceId(), record);
    }

    @Override
    public synchronized FlowInstance findByInstanceId(String instanceId) {
        return flowInstancesMap.get(instanceId);
    }

    @Override
    public List<FlowInstance> findByStatus(String status, int page, int size) {
        return paginate(
                flowInstancesMap.values().stream()
                        .filter(wi -> status.equals(wi.status())),
                page, size);
    }

    @Override
    public List<FlowInstance> listAll(int page, int size, Sort sort) {
        Stream<FlowInstance> stream = flowInstancesMap.values().stream();
        return paginate(switch (sort) {
            case START_TIME_ASC -> stream.sorted(Comparator.comparing(
                    wi -> wi.startTime() != null ? wi.startTime() : java.time.Instant.MIN));
            case START_TIME_DESC -> stream.sorted(Comparator.comparing(
                    wi -> wi.startTime() != null ? wi.startTime() : java.time.Instant.MIN,
                    Comparator.reverseOrder()));
            case LAST_UPDATE_ASC -> stream.sorted(Comparator.comparing(
                    wi -> wi.lastUpdateTime() != null ? wi.lastUpdateTime() : java.time.Instant.MIN));
            case LAST_UPDATE_DESC -> stream.sorted(Comparator.comparing(
                    wi -> wi.lastUpdateTime() != null ? wi.lastUpdateTime() : java.time.Instant.MIN,
                    Comparator.reverseOrder()));
            case STATUS_ASC -> stream.sorted(Comparator.comparing(
                    wi -> wi.status() != null ? wi.status() : ""));
            case STATUS_DESC -> stream.sorted(Comparator.comparing(
                    wi -> wi.status() != null ? wi.status() : "",
                    Comparator.reverseOrder()));
        }, page, size);
    }

    private List<FlowInstance> paginate(Stream<FlowInstance> stream, int page, int size) {
        if (size <= 0 || page < 0) {
            return List.of();
        }
        return stream
                .skip((long) page * size)
                .limit(size)
                .toList();
    }
}
