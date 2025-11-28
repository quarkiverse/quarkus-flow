package io.quarkiverse.flow.config;

import static io.quarkiverse.flow.config.FlowDefinitionsConfig.ROOT_KEY;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = ROOT_KEY)
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FlowDefinitionsConfig {

    String DEFAULT_FLOW_DIR = "src/main/flow";
    String ROOT_KEY = "quarkus.flow.definitions";

    /**
     * Directory where to look for Workflow definition files.
     * <p>
     * It is relative to <code>src/main</code> directory.
     * <p>
     * If you set by example <code>workflows</code>, the Workflow definitions must be located in
     * <code>src/main/workflows</code>.
     */
    @WithDefault(DEFAULT_FLOW_DIR)
    Optional<String> dir();

    NamespaceConfig namespace();

    @ConfigGroup
    interface NamespaceConfig {

        /**
         * Prefix used to construct the {@link Identifier#value()} when generating
         * {@link io.quarkiverse.flow.Flow} and {@link io.serverlessworkflow.impl.WorkflowDefinition} beans.
         * <p>
         * Must be a valid Java package name.
         * <p>
         * Example:
         * <ul>
         * <li>Workflow Document's name: <code>myWorkflow</code></li>
         * <li>Workflow Document's namespace: <code>flow</code></li>
         * <li>Configuration: <code>quarkus.flow.namespace.prefix=my.company</code></li>
         * </ul>
         * <p>
         * Generated identifiers are:
         * <ul>
         * <li><code>@Identifier("my.company.flow.MyWorkflow")</code></li>
         * <li><code>@Identifier("flow:myWorkflow")</code></li>
         * </ul>
         * <p>
         * If <code>quarkus.flow.definitions.namespace.prefix</code> is not set, the namespace declared
         * inside the workflow definition file is used.
         * <p>
         * Following the previous example, the generated identifiers would be:
         * <p>
         * <code>@Identifier("flow.MyWorkflow")</code> and <code>@Identifier("flow:myWorkflow")</code>.
         */
        Optional<String> prefix();

    }

}
