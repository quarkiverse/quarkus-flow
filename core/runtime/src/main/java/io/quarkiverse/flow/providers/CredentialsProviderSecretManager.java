package io.quarkiverse.flow.providers;

import java.util.List;
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

    private static final String ROOT = FlowSecretsConfig.ROOT_KEY;
    private static final String GLOBAL_KEY = ROOT + ".credentials-provider-name";
    private static final String PER_SECRET_KEY = ROOT + ".credentials-provider-names.<secret>";
    private final Map<String, CredentialsProvider> providerBeanCache = new ConcurrentHashMap<>();
    private final Map<String, CredentialsProvider> secretCache = new ConcurrentHashMap<>();
    @Inject
    FlowSecretsConfig flowSecrets;
    @Inject
    @Any
    Instance<CredentialsProvider> providers;
    private volatile boolean hasAnyProviders;
    private volatile Optional<CredentialsProvider> singleProvider = Optional.empty();

    private static String nullSafeTrim(String v) {
        if (v == null)
            return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    @PostConstruct
    void init() {
        final List<CredentialsProvider> all = providers.stream().toList();
        hasAnyProviders = !all.isEmpty();

        if (providers.isResolvable() && !providers.isAmbiguous()) {
            singleProvider = Optional.of(providers.get());
        }

        for (CredentialsProvider p : all) {
            Named n = p.getClass().getAnnotation(Named.class);
            if (n != null && !n.value().isBlank()) {
                providerBeanCache.putIfAbsent(n.value(), p);
            }
        }
    }

    /**
     * Recorder checks this to decide whether to wire this SM or let the SDK fallback handle resolution.
     */
    public boolean hasAnyProviders() {
        return hasAnyProviders;
    }

    @Override
    public Map<String, String> secret(String secretName) {
        CredentialsProvider provider = secretCache.computeIfAbsent(secretName, this::resolveProviderForSecret);
        Map<String, String> creds = provider.getCredentials(secretName);
        if (creds == null || creds.isEmpty()) {
            throw new IllegalStateException("No credentials found for secret '" + secretName
                    + "' from provider @" + namedOf(provider) + " (" + provider.getClass().getName() + ")");
        }
        return creds;
    }

    private CredentialsProvider resolveProviderForSecret(String secretName) {
        String per = nullSafeTrim(flowSecrets.credentialsProviderNames().get(secretName));
        if (per != null) {
            return selectConfigProvider(per);
        }

        Optional<String> global = flowSecrets.credentialsProviderName().map(String::trim).filter(s -> !s.isEmpty());
        if (global.isPresent()) {
            return selectConfigProvider(global.get());
        }

        if (singleProvider.isPresent()) {
            return singleProvider.get();
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
        CredentialsProvider cached = providerBeanCache.get(named);
        if (cached != null)
            return cached;

        Instance<CredentialsProvider> handle = providers.select(NamedLiteral.of(named));
        if (handle.isResolvable() && !handle.isAmbiguous()) {
            CredentialsProvider p = handle.get();
            providerBeanCache.putIfAbsent(named, p);
            return p;
        }
        if (handle.isAmbiguous()) {
            throw new IllegalStateException("Multiple CredentialsProvider beans match @Named='" + named
                    + "'. Available: " + availableProviders());
        }
        throw new IllegalStateException("CredentialsProvider @Named='" + named
                + "' not found. Available: " + availableProviders());
    }

    private String availableProviders() {
        if (!hasAnyProviders)
            return "<none>";
        String names = providerBeanCache.isEmpty() ? "<no @Named providers>"
                : String.join(", ", new TreeSet<>(providerBeanCache.keySet()));
        String classes = providers.stream()
                .map(p -> p.getClass().getName())
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        return "named=[" + names + "], classes=[" + classes + "]";
    }

    private String namedOf(CredentialsProvider p) {
        var n = p.getClass().getAnnotation(Named.class);
        return (n != null && !n.value().isBlank()) ? n.value() : "<unnamed>";
    }

    @Override
    public int priority() {
        return 100;
    }
}
