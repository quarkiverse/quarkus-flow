package io.quarkiverse.flow.config;

import static io.quarkiverse.flow.config.FlowConfig.ROOT_KEY;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = ROOT_KEY)
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FlowConfig {

    String ROOT_KEY = "quarkus.flow.definitions";

    /**
     * Directory where to look for Workflow definition files.
     * <p>
     * It is relative to <code>src/main</code> directory.
     * <p>
     * If you set by example <code>workflows</code>, the Workflow definitions must be located in
     * <code>src/main/workflows</code>.
     */
    @WithDefault("flow")
    Optional<String> dir();

}
