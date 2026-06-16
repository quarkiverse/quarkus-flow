package io.quarkiverse.flow.runner.model;

import java.util.Objects;

import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import io.serverlessworkflow.api.WorkflowFormat;

public final class WorkflowFormatUtils {

    private WorkflowFormatUtils() {
    }

    public static WorkflowFormat mediaTypeToFormat(final HttpHeaders headers) {
        return headers.getAcceptableMediaTypes().stream()
                .map(mediaType -> {
                    if (mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
                        return WorkflowFormat.JSON;
                    } else if (mediaType.isCompatible(MediaType.valueOf("application/yaml"))) {
                        return WorkflowFormat.YAML;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new NotSupportedException(
                        "Unsupported Media Type for Workflow format. Available Media Types are 'application/yaml' or 'application/json'"));
    }

    public static MediaType formatToMediaType(WorkflowFormat workflowFormat) {
        return switch (workflowFormat) {
            case JSON -> MediaType.APPLICATION_JSON_TYPE;
            case YAML -> MediaType.valueOf("application/yaml");
        };
    }

}
