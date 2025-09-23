package io.quarkiverse.flow;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Carries a registry of @WorkflowDefinitions found in the classpath
 */
@Unremovable
@ApplicationScoped
public class FlowRegistry {
    private final Map<String, Workflow> byId = new ConcurrentHashMap<>();
    private final Map<FlowKey, String> methodToId = new ConcurrentHashMap<>();

    public void register(String id, Workflow workflow, FlowKey workflowKey) {
        if (byId.putIfAbsent(id, workflow) != null) {
            throw new IllegalStateException("Workflow already registered: " + id);
        }
        methodToId.put(workflowKey, id);
    }

    public Workflow get(String id) {
        final Workflow workflow = byId.get(id);
        if (workflow == null) {
            throw new IllegalStateException("Workflow not found: " + id);
        }
        return workflow;
    }

    public Workflow get(String className, String methodName) {
        final Workflow workflow = byId.get(idFor(className, methodName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No workflow found for class: " + className + ", methodName: " + methodName)));
        if (workflow == null) {
            throw new IllegalStateException("No workflow found for class: " + className + ", methodName: " + methodName);
        }
        return workflow;
    }

    public Workflow get(MethodRef methodRef) {
        return get(methodRef.ownerClass, methodRef.methodName);
    }

    public void clear() {
        byId.clear();
    }

    public Set<String> Ids() {
        return byId.keySet();
    }

    public Optional<String> idFor(String className, String methodName) {
        return Optional.ofNullable(methodToId.get(new FlowKey(className, methodName)));
    }
}
