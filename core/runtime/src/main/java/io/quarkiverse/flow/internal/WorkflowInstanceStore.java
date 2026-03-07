package io.quarkiverse.flow.internal;

import java.util.List;

/**
 * Store abstraction for workflow instances in dev mode.
 */
public interface WorkflowInstanceStore {

    /**
     * Save or update a workflow instance record.
     * Uses instanceId as the primary key.
     */
    void saveOrUpdate(FlowInstance record);

    /**
     * Find workflow instances by workflow ID with pagination.
     */
    FlowInstance findByInstanceId(String instanceId);

    /**
     * Find workflow instances by status with pagination.
     */
    List<FlowInstance> findByStatus(String status, int page, int size);

    /**
     * List all workflow instances with pagination and sorting.
     */
    List<FlowInstance> listAll(int page, int size, Sort sort);

    /**
     * Sorting options for listing workflow instances.
     */
    enum Sort {
        START_TIME_ASC,
        START_TIME_DESC,
        LAST_UPDATE_ASC,
        LAST_UPDATE_DESC,
        STATUS_ASC,
        STATUS_DESC
    }
}
