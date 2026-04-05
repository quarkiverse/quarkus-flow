package io.quarkiverse.flow.health;

import java.util.Arrays;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import io.quarkiverse.flow.internal.WorkflowApplicationInfo;
import io.quarkiverse.flow.internal.WorkflowRegistryInitializer;

@Readiness
@ApplicationScoped
public class WorkflowEngineHealthCheck implements HealthCheck {

    @Inject
    WorkflowRegistryInitializer initializer;

    private static final String NAME = "WorkflowApplication Engine Readiness";

    @Override
    public HealthCheckResponse call() {
        WorkflowApplicationInfo info = initializer.getAppInfo();
        HealthCheckResponseBuilder builder = HealthCheckResponse.named(NAME);

        if (info.ready()) {
            return builder.up().withData("appId", info.id()).build();
        }

        builder.down();

        if (info.reasonDown() != null) {
            builder.withData("reason", info.reasonDown().getMessage());
            builder.withData("stackTrace", Arrays.toString(info.reasonDown().getStackTrace()));
        } else {
            builder.withData("reason", "Engine is warming up or waiting for another threads...");
        }

        return builder.build();
    }
}