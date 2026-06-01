package io.quarkiverse.flow.runner;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for Quarkus Flow Runner endpoints.
 * <p>
 * Controls which REST endpoints are included in the application at build time.
 * This is particularly useful for immutable cloud deployments where definition
 * management operations should be excluded from the native image or JAR.
 * <p>
 * Configuration example for cloud deployments:
 *
 * <pre>
 * # Exclude definition management endpoints (POST/PUT/DELETE)
 * quarkus.flow.runner.endpoints.definition.enabled=false
 * </pre>
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/52">Issue #52</a>
 */
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.flow.runner.endpoints")
public interface FlowRunnerBuildTimeConfig {

    /**
     * Definition management endpoint configuration.
     *
     * @return the definition endpoint configuration
     */
    Definition definition();

    /**
     * Configuration for workflow definition management endpoints.
     * <p>
     * When disabled, the POST, PUT, and DELETE endpoints for {@code /runner/definition}
     * are excluded from the build, making the deployment immutable. The GET endpoint
     * remains available for workflow discovery.
     */
    interface Definition {

        /**
         * Enable or disable definition management endpoints at build time.
         * <p>
         * When {@code true} (default), all CRUD operations are available:
         * <ul>
         * <li>GET /runner/definition - List workflows (always available)</li>
         * <li>POST /runner/definition - Create new workflow definitions</li>
         * <li>PUT /runner/definition/{namespace}/{name}/{version} - Update workflow</li>
         * <li>DELETE /runner/definition/{namespace}/{name}/{version} - Delete workflow</li>
         * </ul>
         * <p>
         * When {@code false}, only the GET endpoint is included in the build.
         * This is recommended for production cloud deployments where workflows
         * are loaded from ConfigMaps or filesystem mounts and should not be
         * modified at runtime.
         *
         * @return {@code true} if definition management endpoints should be included (default),
         *         {@code false} to exclude POST/PUT/DELETE operations
         */
        @WithDefault("true")
        boolean enabled();
    }

}
