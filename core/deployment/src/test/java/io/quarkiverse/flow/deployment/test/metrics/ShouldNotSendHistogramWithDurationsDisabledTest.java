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
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

public class ShouldNotSendHistogramWithDurationsDisabledTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> {
                jar.addClass(SimpleFlow.class);
            })
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus", Version.getVersion())))
            .withConfigurationResource("metrics-enabled-with-durations-disabled.properties");

    @Inject
    SimpleFlow flow;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void should_not_send_metrics_using_percentiles_when_there_is_no_percentile() {

        flow.instance(Map.of("message", "hello"))
                .start()
                .join();

        Timer timer = meterRegistry.find("quarkus.flow.workflow.duration")
                .tag("workflow", "named")
                .timer();

        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();

        Assertions.assertNotNull(histogramSnapshot.histogramCounts());
        Assertions.assertTrue(histogramSnapshot.histogramCounts().length > 0);

        // At least one bucket must have data
        boolean hasCounts = Arrays.stream(histogramSnapshot.histogramCounts())
                .anyMatch(c -> c.count() > 0);

        Assertions.assertTrue(hasCounts, "Histogram buckets are empty");
    }
}
