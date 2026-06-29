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
     * Keys can be:
     * <ul>
     * <li>{@code <namespace>:<name>:<version>:<taskName>} — task-level override</li>
     * <li>{@code <namespace>:<name>:<version>} — workflow-level override</li>
     * <li>{@code <namespace>:<name>} — versionless override, applied to all versions of the workflow</li>
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
