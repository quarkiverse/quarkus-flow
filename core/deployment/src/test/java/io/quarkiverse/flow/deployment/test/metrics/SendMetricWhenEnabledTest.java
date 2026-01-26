package io.quarkiverse.flow.deployment.test.metrics;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

public class SendMetricWhenEnabledTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> {
                jar.addClass(SimpleFlow.class);
            })
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus", Version.getVersion())))
            .withConfigurationResource("metrics-enabled.properties");

    @Inject
    SimpleFlow flow;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void should_send_metrics() {

        flow.instance(Map.of("message", "hello"))
                .start()
                .join();

        await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    long total = meterRegistry.getMeters()
                            .stream()
                            .filter(meter -> meter.getId().getName().startsWith("quarkus.flow.workflow."))
                            .count();

                    return total > 0;
                });
    }
}
