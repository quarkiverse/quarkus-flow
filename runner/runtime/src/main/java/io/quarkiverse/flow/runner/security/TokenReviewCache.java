package io.quarkiverse.flow.runner.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkus.arc.Unremovable;

/**
 * Bounded LRU cache of Kubernetes TokenReview results.
 * <p>
 * Caches verified TokenReview outcomes so that repeated requests with the
 * same ServiceAccount token do not hit the Kubernetes API server every time.
 * <p>
 * Keys are SHA-256 hashes of the raw token, so the raw (secret) token bytes
 * are never stored as map keys. Entries are bounded by
 * {@code quarkus.flow.runner.security.embed.kubernetes.cache-max-entries}
 * via LRU eviction ({@code LinkedHashMap} in access order). Expiry is checked
 * lazily at {@link #get(String)} time:
 * <ul>
 * <li>positive results ({@code authenticated=true}) expire at the verified
 * expiry from the TokenReview response; when the response omits it, they
 * expire 5 minutes after insertion</li>
 * <li>negative results ({@code authenticated=false}) are cached with a
 * 1-minute TTL</li>
 * </ul>
 * <p>
 * Thread-safety is a plain {@code synchronized} on the map: authentication
 * traffic is low-QPS, so no concurrency framework is needed.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
@ApplicationScoped
@Unremovable
public class TokenReviewCache {

    /**
     * TTL (in seconds) applied to negative results ({@code authenticated=false}).
     */
    private static final long NEGATIVE_TTL_SECONDS = 60;

    private static final long MISSING_EXPIRY_TTL_SECONDS = 300;

    /**
     * SHA-256 hex-encoded key -> cached entry.
     */
    private final Map<String, CachedEntry> map;

    private final FlowRunnerConfig config;

    @Inject
    public TokenReviewCache(FlowRunnerConfig config) {
        this.config = config;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedEntry> eldest) {
                return size() > maxEntries();
            }
        };
    }

    /**
     * Returns the cached TokenReview result for the given token, if present
     * and not expired.
     *
     * @param token the raw (untrusted) ServiceAccount token
     * @return the cached result, or empty when absent or expired
     */
    public Optional<TokenReviewClient.TokenReviewResult> get(String token) {
        String key = keyFor(token);
        synchronized (map) {
            CachedEntry entry = map.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.expiresAt().isBefore(Instant.now())) {
                // Lazy eviction of stale entries.
                map.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.result());
        }
    }

    /**
     * Caches a TokenReview result for the given token.
     * <p>
     * For positive results the caller-supplied {@code expiresAt} (the verified
     * expiry from the TokenReview response) is honored; when it is
     * {@code null}, the entry expires 5 minutes after insertion. Negative
     * results ({@code authenticated=false}) always get a 1-minute TTL.
     *
     * @param token the raw (untrusted) ServiceAccount token
     * @param result the TokenReview result to cache
     * @param expiresAt the verified expiry for positive results, or
     *        {@code null}
     */
    public void put(String token, TokenReviewClient.TokenReviewResult result, Instant expiresAt) {
        Instant expiry;
        if (result.authenticated()) {
            expiry = expiresAt != null ? expiresAt
                    : Instant.now().plusSeconds(MISSING_EXPIRY_TTL_SECONDS);
        } else {
            expiry = Instant.now().plusSeconds(NEGATIVE_TTL_SECONDS);
        }
        synchronized (map) {
            map.put(keyFor(token), new CachedEntry(result, expiry));
        }
    }

    /**
     * Returns the number of cached entries (including not-yet-evicted stale
     * ones).
     *
     * @return the current number of entries
     */
    public long size() {
        synchronized (map) {
            return map.size();
        }
    }

    /**
     * SHA-256 hex-encoded cache key for a raw token. Package-private test
     * seam.
     */
    static String keyFor(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM specification; unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Snapshot of the current cache keys. Package-private test seam used to
     * assert that raw tokens are never stored.
     */
    Set<String> keySet() {
        synchronized (map) {
            return Set.copyOf(map.keySet());
        }
    }

    private int maxEntries() {
        return Math.max(1, config.security().embed().kubernetes().cacheMaxEntries());
    }

    /**
     * A cached TokenReview result and its expiration instant.
     */
    private record CachedEntry(TokenReviewClient.TokenReviewResult result, Instant expiresAt) {
    }
}
