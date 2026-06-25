package io.quarkiverse.flow.oidc.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * Storage abstraction for exchanged tokens.
 */
public interface TokenCacheRepository {

    /**
     * Store (or replace) a token, linking it to the given workflow instance.
     */
    void store(CachedToken token, String instanceId);

    /**
     * Return a non-expired token for the key, or empty.
     */
    Optional<CachedToken> get(TokenCacheKey key);

    /**
     * Remove a token regardless of links.
     */
    void evict(TokenCacheKey key);

    /**
     * Unlink an instance from all its tokens, evicting any token left with no links.
     */
    void unlinkInstance(String instanceId);

    /**
     * All cached tokens within {@code threshold} of expiry, for proactive refresh.
     */
    Collection<CachedToken> getTokensNearingExpiry(Duration threshold);
}
