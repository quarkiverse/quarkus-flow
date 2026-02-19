package io.quarkiverse.flow.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
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
     * The name of the proxy configuration to use for configuring <b>HTTP</b> proxy.
     * <p>
     * Default client:
     *
     * <pre>
     * quarkus.flow.http.client.proxy-configuration-name=proxy-cfg
     * </pre>
     * <p>
     * Named client:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.proxy-configuration-name=proxy-cfg
     * </pre>
     *
     * There are some rules for using proxy configuration:
     * <ul>
     * <li>If not set and the default proxy configuration is configured ({@code quarkus.proxy.*}) then that will be used.</li>
     * <li>If the proxy configuration name is set, the configuration from {@code quarkus.proxy.<name>.*} will be used.</li>
     * <li>If the proxy configuration name is set, but no proxy configuration is found with that name, then an error will be
     * thrown at runtime.</li>
     * </ul>
     * <p>
     * Use the value {@code none} to disable using the default configuration defined via {@code quarkus.proxy.*}.
     */
    Optional<String> proxyConfigurationName();

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

    ResilienceConfig resilience();

    @ConfigGroup
    interface ResilienceConfig {
        //@formatter:off
        /**
         * Identifier of the {@code TypeGuard<CompletionStage<WorkflowModel>>}
         * implementation to be used by this HTTP client.
         * <p>
         * Default client:
         *
         * <pre>
         * quarkus.flow.http.client.resilience.identifier=my-guard
         * </pre>
         *
         * <p>
         * Named client:
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.identifier=my-guard
         * </pre>
         */
        Optional<String> identifier();
        //@formatter:on

        RetryConfig retry();

        CircuitBreakerConfig circuitBreaker();
    }

    @ConfigGroup
    interface CircuitBreakerConfig {

        /**
         * Enables or disables Fault Tolerance Circuit Breaker support.
         * <p>
         * <strong>Default client configuration:</strong>
         * <p>
         * <code>quarkus.flow.http.client.resilience.circuit-breaker.enabled=true</code>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.circuit-breaker.enabled=true
         * </pre>
         */
        @WithDefault("true")
        Optional<Boolean> enabled();

        /**
         * Defines the ratio of failures in the rolling window that causes a closed circuit breaker to move to open.
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.circuit-breaker.failure-ratio=0.5
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.circuit-breaker.failure-ratio=0.5
         * </pre>
         */
        @WithDefault("0.5")
        OptionalDouble failureRatio();

        /**
         * Defines the size of the rolling window. That is, the number of recent consecutive invocations tracked by a closed
         * circuit breaker.
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.circuit-breaker.request-volume-threshold=20
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.circuit-breaker.request-volume-threshold=20
         * </pre>
         */
        @WithDefault("20")
        OptionalInt requestVolumeThreshold();

        /**
         * Defines the number of probe invocations allowed when the circuit breaker is half-open.
         * If they all succeed, the circuit breaker moves to closed, otherwise it moves back to open.
         *
         * <pre>
         * quarkus.flow.http.client.resilience.circuit-breaker.success-threshold=1
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.circuit-breaker.success-threshold=1
         * </pre>
         */
        @WithDefault("1")
        OptionalInt successThreshold();

        /**
         * The delay after which an open circuit breaker moves to half-open.
         *
         * <pre>
         * quarkus.flow.http.client.resilience.circuit-breaker.delay=5s
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.circuit-breaker.delay=5s
         * </pre>
         */
        @WithDefault("5s")
        Duration delay();

        /**
         * Defines the set of exception types considered failure.
         * <p>
         * The comparison is based on the fully qualified class name of the
         * exception. Subclasses are also considered a match.
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.circuit-breaker.exceptions=
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.circuit-breaker.exceptions=
         * </pre>
         *
         * <p>
         * Example:
         *
         * <pre>
         * quarkus.flow.http.client.resilience.circuit-breaker.exceptions=
         *     java.io.IOException,
         *     jakarta.ws.rs.ProcessingException
         * </pre>
         */
        Optional<List<String>> exceptions();
    }

    @ConfigGroup
    interface RetryConfig {

        /**
         * Defines the maximum number of retries for a task execution.
         * <p>
         * The value must be greater than or equal to {@code -1}.
         * <ul>
         * <li>{@code -1}: retries indefinitely</li>
         * <li>{@code 0}: no retries (the task is executed only once)</li>
         * <li>{@code N > 0}: the task is executed {@code N + 1} times
         * (1 initial execution plus {@code N} retries)</li>
         * </ul>
         *
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.retry.max-retries=3
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.retry.max-retries=3
         * </pre>
         */
        @WithDefault("3")
        OptionalInt maxRetries();

        /**
         * Enables or disables Fault Tolerance retry support.
         * <p>
         * When enabled, failed task executions may be retried according to the
         * configured retry policy.
         *
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.retry.enabled = true
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.retry.enabled=true
         * </pre>
         */
        @WithDefault("true")
        Optional<Boolean> enabled();

        /**
         * Defines the delay between retry attempts.
         * <p>
         * The delay is applied <strong>only after a failed execution</strong> and
         * <strong>before a retry attempt</strong> is performed.
         * <p>
         * The initial execution is not delayed. The delay applies only to subsequent
         * retry attempts.
         * <p>
         * A value of {@code 0} disables the delay, causing retries to be executed
         * immediately.
         *
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resiilience.retry.delay = 0
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.retry.delay=0
         * </pre>
         */
        @WithDefault("0")
        Duration delay();

        /**
         * Defines the jitter bound applied to the delay between retry attempts.
         * <p>
         * A random value in the range from {@code -jitter} to {@code +jitter} is added
         * to the configured retry delay. This helps to avoid retry bursts when multiple
         * tasks fail simultaneously.
         * <p>
         * The jitter is applied only to retry attempts. The initial execution is not
         * affected.
         * <p>
         * A value of {@code 0} disables jitter, causing the retry delay to be applied
         * without any random variation.
         *
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.retry.jitter=200ms
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.retry.jitter=200ms
         * </pre>
         */
        @WithDefault("200ms")
        Duration jitter();

        /**
         * Defines the exception types that trigger a retry.
         * <p>
         * When the execution throws an exception whose type matches one of the
         * configured values, the retry mechanism is applied.
         * <p>
         * The comparison is based on the fully qualified class name of the
         * exception. Subclasses are also considered a match.
         * <p>
         * <strong>Default client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.resilience.retry.exceptions=
         * </pre>
         *
         * <p>
         * <strong>Named client configuration:</strong>
         *
         * <pre>
         * quarkus.flow.http.client.named.&lt;name&gt;.resilience.retry.exceptions=
         * </pre>
         *
         * <p>
         * Example:
         *
         * <pre>
         * quarkus.flow.http.client.resilience.retry.exceptions=
         *     java.io.IOException,
         *     jakarta.ws.rs.ProcessingException
         * </pre>
         */
        Optional<List<String>> exceptions();

    }

}
