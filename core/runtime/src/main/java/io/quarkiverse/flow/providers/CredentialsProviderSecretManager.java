package io.quarkiverse.flow.providers;

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import io.quarkiverse.flow.config.FlowSecretsConfig;
import io.quarkus.credentials.CredentialsProvider;
import io.serverlessworkflow.impl.config.SecretManager;

/**
 * SecretManager bridge that sources Workflow Secrets from Quarkus CredentialsProvider beans.
 * Selection rules:
 * <ol>
 * <li>per-secret override: quarkus.flow.secrets.credentials-provider-names.&gt;secret&lt;=@NamedBean</li>
 * <li>global: quarkus.flow.secrets.credentials-provider-name=@NamedBean</li>
 * <li>single provider on classpath → use it</li>
 * <li>else fail with guidance</li>
 * </ol>
 */
@ApplicationScoped
public class CredentialsProviderSecretManager implements SecretManager {

    private static final String ROOT = FlowSecretsConfig.SECRETS_ROOT_KEY;
    private static final String GLOBAL_KEY = ROOT + ".credentials-provider-name";
    private static final String PER_SECRET_KEY = ROOT + ".credentials-provider-names.<secret>";

    private final Map<String, CredentialsProvider> configProviderBeanCache = new ConcurrentHashMap<>();
    private final Map<String, CredentialsProvider> secretCache = new ConcurrentHashMap<>();

    @Inject
    FlowSecretsConfig flowSecrets;

    @Inject
    @Any
    Instance<CredentialsProvider> providers;

    private volatile Optional<CredentialsProvider> singleProvider = Optional.empty();

    private static String nullSafeTrim(String v) {
        return v == null ? null : v.trim();
    }

    @PostConstruct
    void init() {
        if (providers.isResolvable() && !providers.isAmbiguous()) {
            singleProvider = Optional.of(providers.get());
        } else {
            providers.stream().forEach(p -> {
                Named n = p.getClass().getAnnotation(Named.class);
                if (n != null && !n.value().isBlank()) {
                    configProviderBeanCache.putIfAbsent(n.value(), p);
                }
            });
        }
    }

    /**
     * Recorder checks this to decide whether to wire this SM or let the SDK fallback handle resolution.
     */
    public boolean hasAnyProviders() {
        return !providers.isUnsatisfied();
    }

    @Override
    public Map<String, Object> secret(String secretName) {
        CredentialsProvider provider = secretCache.computeIfAbsent(secretName, this::resolveProviderForSecret);
        Map<String, String> creds = provider.getCredentials(secretName);

        if (creds == null || creds.isEmpty()) {
            return Collections.emptyMap();
        }

        return new LinkedHashMap<>(creds);
    }

    private CredentialsProvider resolveProviderForSecret(String secretName) {
        if (singleProvider.isPresent()) {
            return singleProvider.get();
        }

        String per = nullSafeTrim(flowSecrets.credentialsProviderNames().get(secretName));
        if (per != null) {
            return selectConfigProvider(per);
        }

        Optional<String> global = flowSecrets.credentialsProviderName().filter(s -> !s.isBlank());
        if (global.isPresent()) {
            return selectConfigProvider(global.get());
        }

        throw new IllegalStateException(
                "Multiple or no CredentialsProvider beans found and no selection provided. " +
                        "Set either '" + GLOBAL_KEY + "' or '" + PER_SECRET_KEY.replace("<secret>", secretName) + "'. " +
                        "Available: " + availableProviders());
    }

    /**
     * Centralized config → provider resolution with cache + CDI fallback.
     */
    private CredentialsProvider selectConfigProvider(String named) {
        return configProviderBeanCache.computeIfAbsent(named, k -> {
            Instance<CredentialsProvider> handle = providers.select(NamedLiteral.of(named));
            if (handle.isAmbiguous()) {
                throw new IllegalStateException("Multiple CredentialsProvider beans match @Named='" + named
                        + "'. Available: " + availableProviders());
            }
            if (!handle.isResolvable()) {
                throw new IllegalStateException("CredentialsProvider @Named='" + named
                        + "' not found. Available: " + availableProviders());
            }
            return handle.get();
        });
    }

    private String availableProviders() {
        if (providers.isUnsatisfied())
            return "<none>";
        String names = configProviderBeanCache.isEmpty() ? "<no @Named providers>"
                : String.join(", ", new TreeSet<>(configProviderBeanCache.keySet()));
        String classes = providers.stream()
                .map(p -> p.getClass().getName())
                .sorted()
                .collect(joining(", "));
        return "named=[" + names + "], classes=[" + classes + "]";
    }

    @Override
    public int priority() {
        return 100;
    }
}
