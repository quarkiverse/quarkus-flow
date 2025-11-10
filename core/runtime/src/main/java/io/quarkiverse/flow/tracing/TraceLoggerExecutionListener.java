/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quarkiverse.flow.tracing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.serverlessworkflow.impl.jackson.JsonUtils;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

/**
 * Logging-only execution listener. Emits structured, MDC-enriched lines that are
 * friendly to Kibana/Datadog when JSON logging + include-MDC are enabled.
 */
public final class TraceLoggerExecutionListener implements WorkflowExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(TraceLoggerExecutionListener.class);

    /**
     * Max bytes to include from serialized payloads to keep logs sane.
     */
    private static final int MAX_BYTES = 8_000;

    // MDC keys (stable so dashboards can rely on them)
    private static final String K_INSTANCE = "quarkus.flow.instanceId";
    private static final String K_EVENT = "quarkus.flow.event";
    private static final String K_TIME = "quarkus.flow.time";
    private static final String K_TASK_POS = "quarkus.flow.taskPos";
    private static final String K_TASK_NAME = "quarkus.flow.task";

    private static String pos(TaskEvent ev) {
        return ev.taskContext().position().jsonPointer();
    }

    /**
     * Wraps log emission with MDC population; preserves upstream MDC.
     */
    private static void withMdc(WorkflowEvent ev, String eventName, Runnable r) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        try {
            MDC.put(K_INSTANCE, ev.workflowContext().instanceData().id());
            MDC.put(K_EVENT, eventName);
            MDC.put(K_TIME, ev.eventDate().toString());
            if (ev instanceof TaskEvent taskEv) {
                MDC.put(K_TASK_POS, taskEv.taskContext().position().jsonPointer());
                MDC.put(K_TASK_NAME, taskEv.taskContext().taskName());
            }
            r.run();
        } finally {
            if (snapshot == null)
                MDC.clear();
            else
                MDC.setContextMap(snapshot);
        }
    }

    /**
     * Byte-safe JSON-ish rendering with truncation.
     */
    private static String safe(Object o) {
        if (o == null)
            return "null";
        try {
            byte[] bytes = JsonUtils.mapper().writeValueAsBytes(o);
            if (bytes.length <= MAX_BYTES) {
                return new String(bytes, UTF_8);
            }
            String head = new String(bytes, 0, MAX_BYTES, UTF_8);
            return head + "...(truncated)";
        } catch (Exception ignore) {
            String s = o.toString();
            if (s.getBytes(UTF_8).length <= MAX_BYTES)
                return s;
            byte[] b = s.getBytes(UTF_8);
            return new String(b, 0, MAX_BYTES, UTF_8) + "...(truncated)";
        }
    }

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        withMdc(ev, "workflow.started", () -> log.info(
                "Workflow name={} id={} started at {} input={}",
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().instanceData().id(), ev.eventDate(),
                safe(ev.workflowContext().instanceData().input().asJavaObject())));
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent ev) {
        withMdc(ev, "workflow.resumed", () -> log.info(
                "Workflow name={} id={} resumed at {}",
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().instanceData().id(),
                ev.eventDate()));
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent ev) {
        withMdc(ev, "workflow.suspended", () -> log.info(
                "Workflow name={} id={} suspended at {}",
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().instanceData().id(), ev.eventDate()));
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
        withMdc(ev, "workflow.completed", () -> log.info(
                "Workflow name={} id={} completed at {}",
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().instanceData().id(), ev.eventDate()));
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent ev) {
        withMdc(ev, "workflow.failed", () -> log.info(
                "Workflow name={} id={} failed at {} cause={}",
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().instanceData().id(), ev.eventDate(), safe(ev.cause())));
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
        withMdc(ev, "workflow.cancelled", () -> log.info(
                "Workflow name={} id={} cancelled at {}",
                ev.workflowContext().definition().workflow().getDocument().getName(),
                ev.workflowContext().instanceData().id(), ev.eventDate()));
    }

    // ---- task ----

    @Override
    public void onTaskStarted(TaskStartedEvent ev) {
        withMdc(ev, "task.started", () -> log.info(
                "Task '{}' started at {} pos={}",
                ev.taskContext().taskName(), ev.eventDate(), pos(ev)));
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent ev) {
        withMdc(ev, "task.completed", () -> log.info(
                "Task '{}' completed at {} output={}",
                ev.taskContext().taskName(), ev.eventDate(),
                safe(ev.taskContext().output().asJavaObject())));
    }

    @Override
    public void onTaskFailed(TaskFailedEvent ev) {
        withMdc(ev, "task.failed", () -> log.info(
                "Task '{}' failed at {} output={} cause={}",
                ev.taskContext().taskName(), ev.eventDate(),
                safe(ev.taskContext().output().asJavaObject()), safe(ev.cause())));
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent ev) {
        withMdc(ev, "task.cancelled", () -> log.info(
                "Task '{}' cancelled at {} pos={}",
                ev.taskContext().taskName(), ev.eventDate(), pos(ev)));
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent ev) {
        withMdc(ev, "task.suspended", () -> log.info(
                "Task '{}' suspended at {} pos={}",
                ev.taskContext().taskName(), ev.eventDate(), pos(ev)));
    }

    @Override
    public void onTaskResumed(TaskResumedEvent ev) {
        withMdc(ev, "task.resumed", () -> log.info(
                "Task '{}' resumed at {} pos={}",
                ev.taskContext().taskName(), ev.eventDate(), pos(ev)));
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent ev) {
        withMdc(ev, "task.retried", () -> log.info(
                "Task '{}' retried at {} pos={}",
                ev.taskContext().taskName(), ev.eventDate(), pos(ev)));
    }
}
