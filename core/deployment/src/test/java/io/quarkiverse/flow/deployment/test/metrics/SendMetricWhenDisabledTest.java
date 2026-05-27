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

public class SendMetricWhenDisabledTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> {
                jar.addClass(SimpleFlow.class);
            })
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus", Version.getVersion())))
            .withConfigurationResource("metrics-disabled.properties");

    @Inject
    SimpleFlow flow;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void should_not_send_metrics() {

        flow.instance(Map.of("message", "hello"))
                .start()
                .join();

        await()
                .during(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> {
                    long total = meterRegistry.getMeters()
                            .stream()
                            .filter(meter -> meter.getId().getName()
                                    .startsWith("quarkus.flow.workflow."))
                            .count();

                    org.junit.jupiter.api.Assertions.assertEquals(0, total);
                });
    }

}
