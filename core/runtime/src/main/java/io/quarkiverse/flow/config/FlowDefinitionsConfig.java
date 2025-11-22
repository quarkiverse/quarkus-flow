package io.quarkiverse.flow.config;

import static io.quarkiverse.flow.config.FlowDefinitionsConfig.ROOT_KEY;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = ROOT_KEY)
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FlowDefinitionsConfig {

    String DEFAULT_FLOW_DIR = "flow";
    String DEFAULT_FLOW_NAMESPACE = "io.quarkiverse.flow.generated";
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

    /**
     * Namespace to be used as prefix for {@link Identifier#value()} when generating
     * {@link io.quarkiverse.flow.Flow} and {@link io.serverlessworkflow.impl.WorkflowDefinition} beans.
     * <p>
     * By default, the prefix is <code>io.quarkiverse.flow.generated</code>.
     * <p>
     * It means that if a Workflow definition has name <code>myWorkflow</code> and the namespace is set to
     * <code>my-company</code>, the generated beans will have the identifier
     * <code>@Identifier("io.quarkiverse.flow.generated.my.company.MyWorkflowWorkflow")</code>.
     */
    @WithDefault(DEFAULT_FLOW_NAMESPACE)
    String namespace();

}
