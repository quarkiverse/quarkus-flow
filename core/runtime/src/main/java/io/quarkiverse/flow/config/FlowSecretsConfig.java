package io.quarkiverse.flow.config;

import static io.quarkiverse.flow.config.FlowSecretsConfig.ROOT_KEY;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = ROOT_KEY)
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowSecretsConfig {

    String ROOT_KEY = "quarkus.flow.secrets";

    /**
     * Name of the custom {@link io.quarkus.credentials.CredentialsProvider} bean name to use as a global source for Workflow
     * Secrets.
     * This means that every {@link io.serverlessworkflow.impl.WorkflowDefinition} will use the same credential provider.
     *
     * @see <a href="https://quarkus.io/guides/credentials-provider>Using a Credentials Provider</a>
     * @see <a href="https://github.com/serverlessworkflow/specification/blob/main/dsl.md#secret">CNCF Workflow Specification -
     *      Secrets</a>
     * @see <a href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#authentication">CNCF
     *      Workflow Specification - Authentication</a>
     */
    Optional<String> credentialsProviderName();

    /**
     * Specific custom {@link io.quarkus.credentials.CredentialsProvider} per workflow secret definition.
     * In this case, you can specify one credential provider per {@link io.serverlessworkflow.impl.WorkflowDefinition} secret.
     * Use the secret name as a key, e.g.:
     *
     * <pre>
     *     quarkus.flow.secrets.credentials-provider-names.myCustomSecret=theNamedCredentialsProvider
     * </pre>
     *
     * @see #credentialsProviderName()
     */
    Map<String, String> credentialsProviderNames();

}
