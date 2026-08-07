package io.quarkiverse.flow.config;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Generic 6-level cascade resolver for client configuration overrides.
 * <p>
 * Resolution order (most to least specific):
 * <ol>
 * <li>{@code namespace:name:version.task.taskName} — task full</li>
 * <li>{@code namespace:name.task.taskName} — task medium</li>
 * <li>{@code name.task.taskName} — task short</li>
 * <li>{@code namespace:name:version} — workflow full</li>
 * <li>{@code namespace:name} — workflow medium</li>
 * <li>{@code name} — workflow short</li>
 * </ol>
 *
 * @see ClientNamingConvention
 */
public final class ClientConfigCascade {

    private ClientConfigCascade() {
    }

    /**
     * Walks the 6-level cascade and returns the first non-null result from {@code lookup}.
     *
     * @param <T> the type returned by the lookup (e.g. {@code String}, {@code Duration})
     * @param lookup maps a config key to a value, returning {@code null} when the key is absent
     * @param workflowId the workflow identity
     * @param taskName the task name (maybe {@code null} or blank to skip task-level keys)
     * @return the first non-null lookup result, or {@code null} if no key matches
     */
    public static <T> T resolve(Function<String, T> lookup, WorkflowDefinitionId workflowId, String taskName) {
        return cascadeKeys(workflowId, taskName)
                .map(lookup)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Stream<String> cascadeKeys(WorkflowDefinitionId workflowId, String taskName) {
        Stream<String> workflowKeys = Stream.of(
                ClientNamingConvention.workflowKeyFull(workflowId),
                ClientNamingConvention.workflowKeyMedium(workflowId),
                ClientNamingConvention.workflowKeyShort(workflowId));

        if (taskName == null || taskName.isBlank()) {
            return workflowKeys;
        }

        return Stream.concat(
                Stream.of(
                        ClientNamingConvention.taskKeyFull(workflowId, taskName),
                        ClientNamingConvention.taskKeyMedium(workflowId, taskName),
                        ClientNamingConvention.taskKeyShort(workflowId, taskName)),
                workflowKeys);
    }
}
