package io.quarkiverse.flow.devui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.internal.FlowInstance;
import io.quarkiverse.flow.internal.WorkflowInstanceStore;
import io.quarkus.runtime.annotations.JsonRpcDescription;

@ApplicationScoped
public class ManagementLifecycleRPCService {

    private static final int MAX_PAGE_SIZE = 1000;
    private final WorkflowInstanceStore store;

    public ManagementLifecycleRPCService(WorkflowInstanceStore store) {
        this.store = store;
    }

    @JsonRpcDescription("Get all workflow instances with pagination and sorting")
    public List<FlowInstance> listAllWorkflowInstances(
            @JsonRpcDescription("Page number (0-based)") Integer page,
            @JsonRpcDescription("Page size") Integer size,
            @JsonRpcDescription("Sort order") String sort) {
        Integer finalPage = validatePage(page);
        Integer finalSize = validateSize(size);
        String finalSort = Optional.ofNullable(sort).orElse(WorkflowInstanceStore.Sort.START_TIME_ASC.name());
        WorkflowInstanceStore.Sort sortEnum = parseSortOrder(finalSort);
        return store.listAll(finalPage, finalSize, sortEnum);
    }

    private static Integer validatePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    private static Integer validateSize(Integer size) {
        if (size == null || size < 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    @JsonRpcDescription("Find workflow instance by workflow ID")
    public FlowInstance findByWorkflowId(
            @JsonRpcDescription("Workflow Instance ID") String instanceId) {
        return store.findByInstanceId(Objects.requireNonNull(instanceId, "instanceId must not be null"));
    }

    @JsonRpcDescription("Find workflow instances by status")
    public List<FlowInstance> findByStatus(
            @JsonRpcDescription("Status") String status,
            @JsonRpcDescription("Page number (0-based)") Integer page,
            @JsonRpcDescription("Page size") Integer size) {
        Objects.requireNonNull(status, "status must not be null");
        Integer finalPage = validatePage(page);
        Integer finalSize = validateSize(size);
        return store.findByStatus(status, finalPage, finalSize);
    }

    private WorkflowInstanceStore.Sort parseSortOrder(String sort) {
        if (sort == null || sort.isEmpty()) {
            return WorkflowInstanceStore.Sort.LAST_UPDATE_DESC;
        }
        try {
            return WorkflowInstanceStore.Sort.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WorkflowInstanceStore.Sort.LAST_UPDATE_DESC;
        }
    }
}
