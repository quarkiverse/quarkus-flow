package io.quarkiverse.flow.deployment.test.metrics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

public class ShouldSendMetricsUsingPercentilesTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> {
                jar.addClass(SimpleFlow.class);
            })
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus", Version.getVersion())))
            .withConfigurationResource("metrics-enabled-with-percentiles.properties");

    @Inject
    SimpleFlow flow;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void should_send_metrics_using_percentiles() {

        flow.instance(Map.of("message", "hello"))
                .start()
                .join();

        Timer timer = meterRegistry.find("quarkus.flow.workflow.duration")
                .tag("workflow", "named")
                .timer();

        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();

        ValueAtPercentile[] percentiles = histogramSnapshot.percentileValues();

        Assertions.assertTrue(Arrays.stream(percentiles).anyMatch(p -> p.percentile() == 0.5), "p50 not found");
        Assertions.assertTrue(Arrays.stream(percentiles).anyMatch(p -> p.percentile() == 0.95), "p95 not found");
        Assertions.assertTrue(Arrays.stream(percentiles).anyMatch(p -> p.percentile() == 0.99), "p95 not found");
    }
}
