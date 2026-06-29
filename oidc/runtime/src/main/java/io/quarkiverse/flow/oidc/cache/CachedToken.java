package io.quarkiverse.flow.oidc.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * An exchanged/acquired token plus the metadata needed for expiry and lifecycle tracking.
 */
public record CachedToken(
        TokenCacheKey key,
        String token,
        Instant expiresAt,
        Instant createdAt,
        Set<String> linkedInstances) {

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isNearingExpiry(Duration threshold) {
        return expiresAt != null && Instant.now().plus(threshold).isAfter(expiresAt);
    }
}
