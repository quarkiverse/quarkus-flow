package io.quarkiverse.flow.internal;

import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowUtils;

public final class WorkflowNameUtils {

    private static final int MAX_LENGTH = 63;

    private WorkflowNameUtils() {
    }

    public static WorkflowDefinitionId newId(Class<?> clazz) {
        return new WorkflowDefinitionId(clazz.getPackageName(), safeNameFromClass(clazz, null),
                WorkflowDefinitionId.DEFAULT_VERSION);
    }

    public static String yamlDescriptorIdentifier(String namespace, String name) {
        return String.format("%s:%s", namespace, name);
    }

    public static String yamlDescriptorIdentifier(WorkflowDefinitionId workflowDefinitionId) {
        return String.format("%s:%s", workflowDefinitionId.namespace(), workflowDefinitionId.name());
    }

    public static String safeNameFromClass(Class<?> clazz, String defaultValue) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        return safeName(clazz.getSimpleName(), defaultValue);
    }

    public static String safeName(String name) {
        return safeName(name, null);
    }

    public static String safeName(String name, String defaultValue) {
        if (!WorkflowUtils.isValid(name)) {
            if (!WorkflowUtils.isValid(defaultValue)) {
                throw new IllegalArgumentException("Simple name and defaultValue cannot be null or blank");
            }
            return defaultValue;
        }

        String sanitizedName = name.trim()
                .replaceAll("([A-Z])([A-Z][a-z])", "$1-$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9-]", "-")
                .toLowerCase()
                .replaceAll("-+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        // Fallback if everything got stripped
        if (sanitizedName.isEmpty()) {
            // won't return here since we don't know if defaultValue is safe or not.
            sanitizedName = (WorkflowUtils.isValid(defaultValue)) ? "wf-" + safeName(defaultValue, "wf") : "wf";
        }

        if (sanitizedName.length() > MAX_LENGTH) {
            sanitizedName = sanitizedName.substring(0, MAX_LENGTH).replaceAll("-+$", "");
            if (sanitizedName.isEmpty()) {
                sanitizedName = defaultValue;
            }
        }

        if (!Character.isLetterOrDigit(sanitizedName.charAt(0))) {
            sanitizedName = "wf-" + sanitizedName;
            if (sanitizedName.length() > MAX_LENGTH) {
                sanitizedName = sanitizedName.substring(0, MAX_LENGTH).replaceAll("-+$", "");
            }
        }

        return sanitizedName;
    }
}
