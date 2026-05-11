package io.quarkiverse.flow.opentelemetry.it.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndexedSpanInfo {
    public static class TaskSpanKey {
        public String taskId;
        public Integer iteration;
        public Integer retryAttempt;
        public Boolean retrying;

        public TaskSpanKey(String taskId, Integer iteration, Integer retryAttempt, Boolean retrying) {
            this.taskId = taskId;
            this.iteration = iteration;
            this.retryAttempt = retryAttempt;
            this.retrying = retrying;
        }

        @Override
        public String toString() {
            return taskId + "|" + iteration + "|" + retryAttempt + "|" + retrying;
        }

        public static TaskSpanKey from(SpanInfo spanInfo) {
            return new TaskSpanKey(spanInfo.getTaskId(), spanInfo.getTaskIteration(), spanInfo.getTaskRetryAttempt(),
                    spanInfo.getTaskRetrying());
        }

        public static TaskSpanKey from(String taskId, int iteration, int retryAttempt, boolean retrying) {
            return new TaskSpanKey(taskId, iteration, retryAttempt, retrying);
        }

        public static TaskSpanKey from(String taskId, int iteration) {
            return new TaskSpanKey(taskId, iteration, 0, false);
        }

        public static TaskSpanKey from(String taskId) {
            return new TaskSpanKey(taskId, 1, 0, false);
        }

    }

    public static class WorkflowSpanKey {
        String workflowName;
        String workflowVersion;

        private WorkflowSpanKey(String workflowName, String workflowVersion) {
            this.workflowName = workflowName;
            this.workflowVersion = workflowVersion;
        }

        public static WorkflowSpanKey from(SpanInfo spanInfo) {
            return new WorkflowSpanKey(spanInfo.getWorkflowName(), spanInfo.getWorkflowVersion());
        }

        public static WorkflowSpanKey from(String workflowName, String workflowVersion) {
            return new WorkflowSpanKey(workflowName, workflowVersion);
        }

        @Override
        public String toString() {
            return workflowName + "|" + workflowVersion;
        }
    }

    private final Map<String, SpanInfo> indexedTaskSpans;

    private final Map<String, SpanInfo> indexedWorkflowSpans;

    private IndexedSpanInfo(Map<String, SpanInfo> indexedTaskSpans, Map<String, SpanInfo> indexedWorkflowSpans) {
        this.indexedTaskSpans = indexedTaskSpans;
        this.indexedWorkflowSpans = indexedWorkflowSpans;
    }

    public SpanInfo getSpanInfo(TaskSpanKey key) {
        return indexedTaskSpans.get(key.toString());
    }

    public SpanInfo getSpanInfo(WorkflowSpanKey key) {
        return indexedWorkflowSpans.get(key.toString());
    }

    public static IndexedSpanInfo from(List<SpanInfo> spanInfoList) {
        Map<String, SpanInfo> indexedTaskSpans = new HashMap<>();
        Map<String, SpanInfo> indexedWorkflowSpans = new HashMap<>();
        for (SpanInfo spanInfo : spanInfoList) {
            if (spanInfo.getTaskId() != null) {
                indexedTaskSpans.put(TaskSpanKey.from(spanInfo).toString(), spanInfo);
            } else {
                indexedWorkflowSpans.put(WorkflowSpanKey.from(spanInfo).toString(), spanInfo);
            }
        }
        return new IndexedSpanInfo(indexedTaskSpans, indexedWorkflowSpans);
    }

    public Map<String, SpanInfo> getIndexedTaskSpans() {
        return indexedTaskSpans;
    }

    public Map<String, SpanInfo> getIndexedWorkflowSpans() {
        return indexedWorkflowSpans;
    }
}
