package io.quarkiverse.flow.providers;

import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.CAPTURE_STACKTRACE;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.CONNECTION_POOL_SIZE;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.CONNECTION_TTL;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.KEEP_ALIVE_ENABLED;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.LOGGING_BODY_LIMIT;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.LOGGING_SCOPE;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.MAX_HEADER_SIZE;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.MAX_INITIAL_LINE_LENGTH;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.MAX_REDIRECTS;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.NAME;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.SHARED;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.STATIC_HEADERS;
import static org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties.USER_AGENT;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl;
import org.jboss.resteasy.reactive.common.jaxrs.MultiQueryParamMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.quarkiverse.flow.config.HttpClientConfig;
import io.quarkus.arc.Arc;
import io.quarkus.proxy.ProxyConfiguration;
import io.quarkus.proxy.ProxyConfigurationRegistry;

/**
 * Registry of JAX-RS {@link Client} instances used by Quarkus Flow HTTP/OpenAPI tasks.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Read {@link FlowHttpConfig} / {@link HttpClientConfig} at runtime</li>
 * <li>Lazily create and cache the default client</li>
 * <li>Lazily create and cache named clients</li>
 * <li>Resolve the client to use for a given workflow/task based on routing config</li>
 * </ul>
 *
 * <p>
 * Config shapes (for reference):
 * </p>
 *
 * <pre>
 * # Default client (FlowHttpConfig extends HttpClientConfig)
 * quarkus.flow.http.client.connect-timeout=5000
 * quarkus.flow.http.client.read-timeout=10000
 * quarkus.flow.http.client.logging.scope=request-response
 *
 * # Named clients
 * quarkus.flow.http.client.named.secureA.connect-timeout=3000
 * quarkus.flow.http.client.named.secureA.user-agent=MyCompanyBot/1.0
 *
 * # Workflow-level routing
 * quarkus.flow.http.client.workflow.myFlow.name=secureA
 *
 * # Task-level routing
 * quarkus.flow.http.client.workflow.myFlow.task.fetchCustomers.name=secureB
 * </pre>
 */
@ApplicationScoped
public class HttpClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientProvider.class.getName());
    private static final String DEFAULT_AGENT = "Quarkus Flow REST Client";

    private final FlowHttpConfig config;
    /**
     * Cached named clients, keyed by logical client name (e.g. "secureA").
     */
    private final Map<String, Client> namedClients = new ConcurrentHashMap<>();
    private final RoutingNameResolver routingNameResolver;
    /**
     * Cached default client (using {@link FlowHttpConfig} as {@link HttpClientConfig}).
     */
    private volatile Client defaultClient;

    @Inject
    public HttpClientProvider(FlowHttpConfig config) {
        this.config = config;
        this.routingNameResolver = new RoutingNameResolver(config);
    }

    /**
     * Close all managed clients on shutdown.
     */
    @PreDestroy
    void destroy() {
        if (defaultClient != null) {
            defaultClient.close();
        }
        namedClients.values().forEach(Client::close);
    }

    /**
     * Resolve the {@link Client} to use for the given workflow and task.
     * <p>
     * Resolution order:
     * <ol>
     * <li>Task-level override:
     * {@code quarkus.flow.http.client.workflow.<workflowId>.task.<taskId>.name}</li>
     * <li>Workflow-level default:
     * {@code quarkus.flow.http.client.workflow.<workflowId>.name}</li>
     * <li>Fallback to the global default client ({@link FlowHttpConfig})</li>
     * </ol>
     *
     * @param workflowId workflow id (as defined in the DSL / {@code Workflow#id()})
     * @param taskId task logical id (e.g. {@code "fetchCustomers"})
     * @return a cached {@link Client} instance
     */
    public Client clientFor(String workflowId, String taskId) {
        final String clientName = routingNameResolver.resolveName(workflowId, taskId);
        if (clientName == null) {
            return getOrCreateDefaultClient();
        }
        return getOrCreateNamedClient(clientName);
    }

    /**
     * Build a JAX-RS client from a {@link HttpClientConfig}, mapping our
     * Quarkus Flow config to RESTEasy Reactive / Quarkus rest-client properties.
     */
    private Client buildClient(HttpClientConfig httpCfg) {
        ClientBuilder builder = ClientBuilder.newBuilder();

        httpCfg.connectTimeout()
                .ifPresent(v -> builder.connectTimeout(v, TimeUnit.MILLISECONDS));
        httpCfg.readTimeout()
                .ifPresent(v -> builder.readTimeout(v, TimeUnit.MILLISECONDS));
        httpCfg.connectionPoolSize()
                .ifPresent(v -> builder.property(CONNECTION_POOL_SIZE, v));
        httpCfg.connectionTtl()
                .ifPresent(v -> builder.property(CONNECTION_TTL, v));
        httpCfg.maxRedirects()
                .ifPresent(v -> builder.property(MAX_REDIRECTS, v));
        httpCfg.maxHeaderSize()
                .ifPresent(v -> builder.property(MAX_HEADER_SIZE, v));
        httpCfg.maxInitialLineLength()
                .ifPresent(v -> builder.property(MAX_INITIAL_LINE_LENGTH, v));
        httpCfg.keepAliveEnabled()
                .ifPresent(v -> builder.property(KEEP_ALIVE_ENABLED, v));
        httpCfg.captureStacktrace()
                .ifPresent(v -> builder.property(CAPTURE_STACKTRACE, v));
        httpCfg.shared()
                .ifPresent(v -> builder.property(SHARED, v));
        httpCfg.name()
                .ifPresent(v -> builder.property(NAME, v));
        httpCfg.staticHeaders()
                .ifPresent(v -> builder.property(STATIC_HEADERS, parseStaticHeaders(v)));
        builder.property(USER_AGENT, httpCfg.userAgent().orElse(DEFAULT_AGENT));

        if (builder instanceof ClientBuilderImpl quarkus) {
            httpCfg.http2().ifPresent(quarkus::http2);
            httpCfg.alpn().ifPresent(quarkus::alpn);
            httpCfg.userAgent().ifPresent(quarkus::setUserAgent);
            httpCfg.loggingScope().ifPresent(quarkus::loggingScope);
            httpCfg.loggingBodyLimit().ifPresent(quarkus::loggingBodySize);

            ProxyConfigurationRegistry proxyConfigurationRegistry = Arc.container().select(ProxyConfigurationRegistry.class)
                    .get();

            Optional<ProxyConfiguration> proxy = proxyConfigurationRegistry.get(httpCfg.proxyConfigurationName());
            proxy.map(ProxyConfiguration::assertHttpType)
                    .ifPresent(proxyConfiguration -> {
                        quarkus.proxy(proxyConfiguration.host(), proxyConfiguration.port());
                        proxyConfiguration.username().ifPresent(quarkus::proxyUser);
                        proxyConfiguration.password().ifPresent(quarkus::proxyPassword);
                        proxyConfiguration.nonProxyHosts()
                                .ifPresent(nonProxyHosts -> quarkus.nonProxyHosts(String.join(",", nonProxyHosts)));
                        proxyConfiguration.proxyConnectTimeout().ifPresent(quarkus::proxyConnectTimeout);
                    });

            httpCfg.followRedirects().ifPresent(quarkus::followRedirects);
            httpCfg.enableCompression().ifPresent(quarkus::enableCompression);
            httpCfg.maxChunkSize().ifPresent(quarkus::maxChunkSize);
            httpCfg.http2UpgradeMaxContentLength()
                    .ifPresent(quarkus::http2UpgradeMaxContentLength);

            httpCfg.multiQueryParamMode().ifPresent(mode -> quarkus.multiQueryParamMode(
                    MultiQueryParamMode.valueOf(mode.toUpperCase(Locale.ROOT))));

            httpCfg.trustAll().ifPresent(quarkus::trustAll);
            httpCfg.verifyHost().ifPresent(quarkus::verifyHost);
        } else {
            // Fallback for non-Quarkus ClientBuilder implementations (tests, other environments)
            httpCfg.loggingScope()
                    .ifPresent(v -> builder.property(LOGGING_SCOPE, v.toString().toLowerCase(Locale.ROOT)));
            httpCfg.loggingBodyLimit()
                    .ifPresent(v -> builder.property(LOGGING_BODY_LIMIT, v));
        }

        return builder.build();
    }

    private Client getOrCreateDefaultClient() {
        final Client existing = defaultClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (defaultClient == null) {
                LOG.debug("Creating default HttpClient");
                defaultClient = buildClient(config);
            }
            return defaultClient;
        }
    }

    private Client buildNamedClient(String name) {
        HttpClientConfig namedConfig = config.named().get(name);
        if (namedConfig == null) {
            LOG.debug("Using default HTTP client for '{}'", name);
            return getOrCreateDefaultClient();
        }
        LOG.debug("Creating named HttpClient '{}'", name);
        return buildClient(namedConfig);
    }

    private Client getOrCreateNamedClient(String name) {
        return namedClients.computeIfAbsent(name, this::buildNamedClient);
    }

    private Map<String, String> parseStaticHeaders(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq == trimmed.length() - 1) {
                LOG.warn("Ignoring invalid static header entry '{}'. Expected 'name=value'", trimmed);
                continue;
            }

            String name = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();

            if (!name.isEmpty()) {
                result.put(name, value);
            }
        }

        return result;
    }

}
