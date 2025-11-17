package io.quarkiverse.flow.config;

import java.util.Optional;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import io.smallrye.config.WithName;

/**
 * Shared HTTP client tuning configuration.
 * <p>
 * This interface is reused by:
 * <ul>
 * <li>the default HTTP client (via {@link FlowHttpConfig} extending it)</li>
 * <li>named HTTP clients ({@code Map&lt;String, HttpClientConfig&gt;})</li>
 * </ul>
 * <p>
 * All properties live under the {@code quarkus.flow.http.client} namespace.
 * <p>
 * For the default client:
 *
 * <pre>
 * quarkus.flow.http.client.connect-timeout=5000
 * </pre>
 * <p>
 * For a named client:
 *
 * <pre>
 * quarkus.flow.http.client.named.secureA.connect-timeout=5000
 * </pre>
 * <p>
 * Internally, these properties are mapped to
 * {@link org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl} and then to Vert.x
 * {@code HttpClientOptions}, but this interface is meant to be user-facing configuration.
 */
public interface HttpClientConfig {

    /**
     * Connect timeout in milliseconds.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.connect-timeout=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.connect-timeout=...
     * </pre>
     *
     * @return the connect timeout in milliseconds, if configured
     */
    Optional<Integer> connectTimeout();

    /**
     * Read timeout in milliseconds.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.read-timeout=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.read-timeout=...
     * </pre>
     *
     * @return the read timeout in milliseconds, if configured
     */
    Optional<Integer> readTimeout();

    /**
     * Maximum size of the HTTP connection pool (number of concurrent connections).
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.connection-pool-size=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.connection-pool-size=...
     * </pre>
     *
     * @return the maximum connection pool size, if configured
     */
    Optional<Integer> connectionPoolSize();

    /**
     * Whether HTTP keep-alive is enabled.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.keep-alive-enabled=true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.keep-alive-enabled=true
     * </pre>
     *
     * @return {@code true} if keep-alive is enabled, if configured
     */
    Optional<Boolean> keepAliveEnabled();

    /**
     * Connection time-to-live in seconds.
     * <p>
     * After this time, idle connections can be closed and reestablished.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.connection-ttl=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.connection-ttl=...
     * </pre>
     *
     * @return the connection TTL in seconds, if configured
     */
    Optional<Integer> connectionTtl();

    /**
     * Quarkus REST client name used when the underlying HTTP client is shared.
     * <p>
     * This name corresponds to the Vert.x HTTP client name and is only used when
     * {@link #shared()} is {@code true}.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.name=flow-default
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.name=flow-secureA
     * </pre>
     *
     * @return the HTTP client name, if configured
     */
    Optional<String> name();

    /**
     * Whether this HTTP client is shared with other Quarkus REST clients.
     * <p>
     * When {@code true}, the underlying Vert.x {@code HttpClient} can be reused across
     * multiple REST clients based on the {@link #name()}.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.shared = true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.shared=true
     * </pre>
     *
     * @return {@code true} if the client is shared, if configured
     */
    Optional<Boolean> shared();

    /**
     * Maximum number of redirects allowed for a request.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.max-redirects=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.max-redirects=...
     * </pre>
     *
     * @return the maximum redirects, if configured
     */
    Optional<Integer> maxRedirects();

    /**
     * Maximum size of all HTTP headers for HTTP/1.x, in bytes.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.max-header-size=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.max-header-size=...
     * </pre>
     *
     * @return the maximum header size, if configured
     */
    Optional<Integer> maxHeaderSize();

    /**
     * Maximum length of the initial HTTP/1.x request line, in bytes.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.max-initial-line-length=...
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.max-initial-line-length=...
     * </pre>
     *
     * @return the maximum initial line length, if configured
     */
    Optional<Integer> maxInitialLineLength();

    /**
     * HTTP {@code User-Agent} header to use for requests.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.user-agent=MyApp/1.0
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.user-agent=MyCompanyBot/1.0
     * </pre>
     *
     * @return the user-agent string, if configured
     */
    Optional<String> userAgent();

    /**
     * Enables HTTP/2 for this client when set to {@code true}.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.http2 = true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.http2=true
     * </pre>
     *
     * @return {@code true} if HTTP/2 is enabled, if configured
     */
    Optional<Boolean> http2();

    /**
     * Enables ALPN (Application-Layer Protocol Negotiation) for this client
     * when set to {@code true}.
     * <p>
     * ALPN is typically required for HTTP/2 over TLS.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.alpn = true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.alpn=true
     * </pre>
     *
     * @return {@code true} if ALPN is enabled, if configured
     */
    Optional<Boolean> alpn();

    /**
     * Whether to capture the stack trace of REST client invocations.
     * <p>
     * This is mainly useful for debugging and error reporting, at the cost of some
     * overhead.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.capture-stacktrace=true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.capture-stacktrace=true
     * </pre>
     *
     * @return {@code true} if stacktrace capture is enabled, if configured
     */
    Optional<Boolean> captureStacktrace();

    /**
     * Logging scope for the underlying REST client.
     * <p>
     * Supported values (case-insensitive):
     * <ul>
     * <li>{@code request-response}</li>
     * <li>{@code all}</li>
     * <li>{@code none}</li>
     * </ul>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.logging.scope = request - response
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.logging.scope=request-response
     * </pre>
     *
     * @return the logging scope value, if configured
     */
    @WithName("logging.scope")
    Optional<LoggingScope> loggingScope();

    /**
     * Maximum number of characters of the request/response body to log.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.logging.body-limit=100
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.logging.body-limit=100
     * </pre>
     *
     * @return the body logging limit in characters, if configured
     */
    @WithName("logging.body-limit")
    Optional<Integer> loggingBodyLimit();

    /**
     * Static HTTP headers to apply to every request by this client.
     * <p>
     * The value is parsed as a comma-separated list of {@code name=value} pairs,
     * for example:
     *
     * <pre>
     * quarkus.flow.http.client.static-headers=X-Env=prod,X-Tenant=acme
     * </pre>
     * <p>
     * For a named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.secureA.static-headers=Authorization=Bearer xyz
     * </pre>
     *
     * @return the raw static headers configuration string, if configured
     */
    Optional<String> staticHeaders();

    // -------------------------------------------------------------------------
    // Proxy configuration
    // -------------------------------------------------------------------------

    /**
     * HTTP proxy host.
     * <p>
     * When set together with {@link #proxyPort()}, requests are sent through the
     * given HTTP proxy.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.proxy-host=proxy.mycorp.local
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.proxy-host=proxy.mycorp.local
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.proxy(host, port)}.
     *
     * @return the proxy host, if configured
     */
    Optional<String> proxyHost();

    /**
     * HTTP proxy port.
     * <p>
     * Only used when {@link #proxyHost()} is also set.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.proxy-port=8080
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.proxy-port=8080
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.proxy(host, port)}.
     *
     * @return the proxy port, if configured
     */
    Optional<Integer> proxyPort();

    /**
     * Username for authenticating with the HTTP proxy.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.proxy-user=svc-flow
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.proxy-user=svc-flow
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.proxyUser(user)}.
     *
     * @return the proxy username, if configured
     */
    Optional<String> proxyUser();

    /**
     * Password for authenticating with the HTTP proxy.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.proxy-password=secret
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.proxy-password=secret
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.proxyPassword(password)}.
     *
     * @return the proxy password, if configured
     */
    Optional<String> proxyPassword();

    /**
     * Hosts that should bypass the HTTP proxy.
     * <p>
     * The value is a {@code |}-separated pattern list, similar to the standard
     * {@code http.nonProxyHosts} format:
     *
     * <pre>
     * quarkus.flow.http.client.non-proxy-hosts=localhost|127.*|[::1]
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.non-proxy-hosts=localhost|127.*
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.nonProxyHosts(hostsPattern)}.
     *
     * @return the non-proxy hosts pattern, if configured
     */
    Optional<String> nonProxyHosts();

    /**
     * Proxy connect timeout in milliseconds.
     * <p>
     * Controls how long the client will wait when establishing a connection
     * to the configured HTTP proxy.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.proxy-connect-timeout=3000
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.proxy-connect-timeout=3000
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.proxyConnectTimeout(Duration)}.
     *
     * @return the proxy connect timeout in milliseconds, if configured
     */
    Optional<Long> proxyConnectTimeout();

    // -------------------------------------------------------------------------
    // Behaviour / HTTP-level options
    // -------------------------------------------------------------------------

    /**
     * Whether the client should automatically follow HTTP redirects.
     * <p>
     * When {@code true}, 3xx responses are transparently followed up to
     * {@link #maxRedirects()}.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.follow-redirects=true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.follow-redirects=true
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.followRedirects(boolean)}.
     *
     * @return {@code true} if redirects are followed, if configured
     */
    Optional<Boolean> followRedirects();

    /**
     * Whether HTTP compression is enabled.
     * <p>
     * When {@code true}, the client advertises support for compressed responses
     * and transparently decompresses them when supported by the server.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.enable-compression=true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.enable-compression=true
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.enableCompression(boolean)}.
     *
     * @return {@code true} if compression is enabled, if configured
     */
    Optional<Boolean> enableCompression();

    /**
     * Maximum HTTP chunk size in bytes.
     * <p>
     * This controls the underlying Vert.x HTTP parser chunk size. For most users
     * the default is sufficient; only adjust if you need fine-grained control
     * over streaming behaviour.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.max-chunk-size=8192
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.max-chunk-size=8192
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.maxChunkSize(int)}.
     *
     * @return the maximum chunk size in bytes, if configured
     */
    Optional<Integer> maxChunkSize();

    /**
     * Maximum content length (in bytes) for HTTP/2 clear-text upgrade (h2c).
     * <p>
     * Controls the threshold used when upgrading HTTP/1.1 connections to HTTP/2
     * without TLS.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.http2-upgrade-max-content-length=65536
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.http2-upgrade-max-content-length=65536
     * </pre>
     * <p>
     * Internally mapped to
     * {@code ClientBuilderImpl.http2UpgradeMaxContentLength(int)}.
     *
     * @return the maximum upgrade content length in bytes, if configured
     */
    Optional<Integer> http2UpgradeMaxContentLength();

    /**
     * How multiple query parameters with the same name are encoded.
     * <p>
     * Valid values correspond to
     * {@link org.jboss.resteasy.reactive.common.jaxrs.MultiQueryParamMode}:
     * for example {@code COMMA}, {@code DUPLICATE}, {@code ARRAY}.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.multi-query-param-mode=COMMA
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.multi-query-param-mode=DUPLICATE
     * </pre>
     * <p>
     * Internally mapped to
     * {@code ClientBuilderImpl.multiQueryParamMode(MultiQueryParamMode)}.
     *
     * @return the multi-query parameter mode, if configured
     */
    Optional<String> multiQueryParamMode();

    // -------------------------------------------------------------------------
    // Security toggles
    // -------------------------------------------------------------------------

    /**
     * Whether to trust all TLS certificates.
     * <p>
     * <strong>Warning:</strong> enabling this effectively disables certificate
     * validation and should only be used in development or controlled environments.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.trust-all=false
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.trust-all=false
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.trustAll(boolean)}.
     *
     * @return {@code true} if all certificates are trusted, if configured
     */
    Optional<Boolean> trustAll();

    /**
     * Whether to verify the remote TLS hostname.
     * <p>
     * When {@code false}, hostname verification is disabled. This is insecure
     * and should only be used in development or with great care in production.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.verify-host=true
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.verify-host=true
     * </pre>
     * <p>
     * Internally mapped to {@code ClientBuilderImpl.verifyHost(boolean)}.
     *
     * @return {@code true} if hostname verification is enabled, if configured
     */
    Optional<Boolean> verifyHost();
}
