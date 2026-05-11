package io.quarkiverse.flow.persistence.infinispan.test;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class InfinispanRespClusteredResource implements QuarkusTestResourceLifecycleManager {

    private static final int RESP_PORT = 11222;
    private static final String IMAGE = System.getProperty("infinispan.server.image");

    private Network network;
    private GenericContainer<?> node1;
    private GenericContainer<?> node2;

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();

        node1 = newNode("infinispan-node-1");
        node2 = newNode("infinispan-node-2");

        try {
            Startables.deepStart(Stream.of(node1, node2)).join();
            assertClusterFormed();
        } catch (RuntimeException e) {
            throw new RuntimeException(
                    "Failed to start Infinispan cluster.\n\n"
                            + "Node 1 running: " + safeRunning(node1) + "\n"
                            + "Node 1 logs:\n" + safeLogs(node1) + "\n\n"
                            + "Node 2 running: " + safeRunning(node2) + "\n"
                            + "Node 2 logs:\n" + safeLogs(node2),
                    e);
        }

        String redisUri = "redis://admin:password@"
                + node1.getHost()
                + ":"
                + node1.getMappedPort(RESP_PORT);

        return Map.of(
                "quarkus.redis.hosts", redisUri,
                "quarkus.redis.devservices.enabled", "false",
                "quarkus.redis.max-pool-size", "64",
                "quarkus.redis.max-pool-waiting", "512");
    }

    private boolean safeRunning(GenericContainer<?> container) {
        try {
            return container != null && container.isRunning();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String safeLogs(GenericContainer<?> container) {
        try {
            return container == null ? "<null>" : container.getLogs();
        } catch (RuntimeException e) {
            return "<unable to read logs: " + e.getMessage() + ">";
        }
    }

    private void assertClusterFormed() {
        String logs1 = node1.getLogs();
        String logs2 = node2.getLogs();

        boolean node1SawTwoMembers = logs1.contains("ISPN000094")
                && logs1.matches("(?s).*Received new cluster view.*\\(2\\).*");

        boolean node2SawTwoMembers = logs2.contains("ISPN000094")
                && logs2.matches("(?s).*Received new cluster view.*\\(2\\).*");

        if (!node1SawTwoMembers && !node2SawTwoMembers) {
            throw new IllegalStateException(
                    "Infinispan servers did not form a 2-node cluster.\n\n"
                            + "Node 1 logs:\n" + logs1 + "\n\n"
                            + "Node 2 logs:\n" + logs2);
        }
    }

    private GenericContainer<?> newNode(String nodeName) {
        return new GenericContainer<>(IMAGE)
                .withStartupAttempts(1)
                .withNetwork(network)
                .withNetworkAliases(nodeName)
                .withExposedPorts(RESP_PORT)
                .withEnv("USER", "admin")
                .withEnv("PASS", "password")
                .withEnv("JAVA_OPTIONS", "-Dinfinispan.node.name=" + nodeName
                        + " -Dinfinispan.server.data.path=/tmp/infinispan-data"
                        + " -Dinfinispan.node.name=" + nodeName)
                .waitingFor(
                        Wait.forLogMessage(".*Started connector Resp.*\\n", 1)
                                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @Override
    public void stop() {
        if (node2 != null) {
            node2.stop();
        }
        if (node1 != null) {
            node1.stop();
        }
        if (network != null) {
            network.close();
        }
    }
}
