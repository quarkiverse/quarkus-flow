package io.quarkiverse.flow.runner;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.flow.runner.source.watch")
public interface FlowRunnerSourceWatchConfig {

    /**
     * Enable or disable the file watcher on the source path directory.
     * <p>
     * When enabled, the runner periodically polls the configured
     * {@code quarkus.flow.runner.source.path} directory for new workflow
     * definition files and registers them automatically without requiring
     * a pod restart.
     * <p>
     * This is a build-time property. Changing it requires a rebuild.
     *
     * @return {@code true} to enable file watching, {@code false} to disable (default)
     */
    @WithDefault("false")
    boolean enabled();
}
