package io.quarkiverse.flow.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.flow.grpc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowGrpcConfig {

    String DEFAULT_CHANNEL_NAME = "flowGrpc";

    /**
     * gRPC client name overrides keyed by workflow or task identifier.
     * <p>
     * Keys follow the unified naming convention:
     * <ul>
     * <li>{@code <name>} — workflow-level short (99% use case)</li>
     * <li>{@code "<namespace>:<name>"} — workflow-level medium</li>
     * <li>{@code "<namespace>:<name>:<version>"} — workflow-level full</li>
     * <li>{@code "<name>.task.<taskName>"} — task-level short</li>
     * <li>{@code "<namespace>:<name>.task.<taskName>"} — task-level medium</li>
     * <li>{@code "<namespace>:<name>:<version>.task.<taskName>"} — task-level full</li>
     * </ul>
     *
     * @return the map of client overrides
     */
    Map<String, ClientOverrideConfig> client();

    /**
     * Override for the Quarkus gRPC client name.
     */
    interface ClientOverrideConfig {

        /**
         * The Quarkus gRPC client name to use, configured under
         * {@code quarkus.grpc.clients.<name>}.
         *
         * @return the client name
         */
        Optional<String> name();
    }
}
