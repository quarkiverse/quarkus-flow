package io.quarkiverse.flow.persistence.infinispan.test;

import java.time.Duration;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class InfinispanRespStandaloneResource implements QuarkusTestResourceLifecycleManager {

    private static final int RESP_PORT = 11222;
    private static final String IMAGE = System.getProperty("infinispan.server.image");

    private Network network;
    private GenericContainer<?> infinispan;

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();

        infinispan = new GenericContainer<>(IMAGE)
                .withStartupAttempts(1)
                .withNetwork(network)
                .withNetworkAliases("infinispan")
                .withExposedPorts(RESP_PORT)
                .withEnv("USER", "admin")
                .withEnv("PASS", "password")
                .withEnv("JAVA_OPTIONS",
                        "-Dinfinispan.server.data.path=/tmp/infinispan-data")
                .waitingFor(
                        Wait.forLogMessage(".*Started connector Resp.*\\n", 1)
                                .withStartupTimeout(Duration.ofMinutes(3)));

        infinispan.start();

        String redisUri = "redis://admin:password@"
                + infinispan.getHost()
                + ":"
                + infinispan.getMappedPort(RESP_PORT);

        return Map.of(
                "quarkus.redis.hosts", redisUri,
                "quarkus.redis.devservices.enabled", "false",
                "quarkus.redis.max-pool-size", "64",
                "quarkus.redis.max-pool-waiting", "512");
    }

    @Override
    public void stop() {
        if (infinispan != null) {
            infinispan.stop();
        }
        if (network != null) {
            network.close();
        }
    }
}
