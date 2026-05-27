package io.quarkiverse.flow.deployment.test.metrics;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.QuarkusUnitTest;

public class SendMetricWithCustomizedPrefixTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(SimpleFlow.class))
            .withConfigurationResource("metrics-enabled-custom-prefix.properties");

    @Inject
    SimpleFlow flow;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void should_send_metrics_with_customized_prefix() {

        flow.instance(Map.of("message", "hello"))
                .start()
                .join();

        await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    long total = meterRegistry.getMeters()
                            .stream()
                            .filter(meter -> meter.getId().getName().startsWith("io.quarkiverse.workflow."))
                            .count();

                    return total > 0;
                });
    }
}
