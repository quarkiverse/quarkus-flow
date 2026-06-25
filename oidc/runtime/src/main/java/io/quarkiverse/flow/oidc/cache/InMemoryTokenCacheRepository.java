package io.quarkiverse.flow.oidc.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

@DefaultBean
@ApplicationScoped
public class InMemoryTokenCacheRepository implements TokenCacheRepository {

    private final ConcurrentHashMap<TokenCacheKey, CachedToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<TokenCacheKey>> instanceTokenLinks = new ConcurrentHashMap<>();

    @Override
    public void store(CachedToken token, String instanceId) {
        TokenCacheKey key = token.key();
        Set<String> links = ConcurrentHashMap.newKeySet();
        links.addAll(token.linkedInstances());
        if (instanceId != null) {
            links.add(instanceId);
            instanceTokenLinks.computeIfAbsent(instanceId, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
        tokens.put(key, new CachedToken(key, token.token(), token.expiresAt(), token.createdAt(), links));
    }

    @Override
    public Optional<CachedToken> get(TokenCacheKey key) {
        CachedToken token = tokens.get(key);
        if (token == null) {
            return Optional.empty();
        }
        if (token.isExpired()) {
            tokens.remove(key, token);
            return Optional.empty();
        }
        return Optional.of(token);
    }

    @Override
    public void evict(TokenCacheKey key) {
        tokens.remove(key);
    }

    @Override
    public Collection<CachedToken> getTokensNearingExpiry(Duration threshold) {
        return tokens.values().stream()
                .filter(t -> !t.isExpired())
                .filter(t -> t.isNearingExpiry(threshold))
                .collect(Collectors.toList());
    }

    @Override
    public void unlinkInstance(String instanceId) {
        Set<TokenCacheKey> keys = instanceTokenLinks.remove(instanceId);
        if (keys == null) {
            return;
        }
        for (TokenCacheKey key : keys) {
            tokens.computeIfPresent(key, (k, token) -> {
                Set<String> updated = ConcurrentHashMap.newKeySet();
                updated.addAll(token.linkedInstances());
                updated.remove(instanceId);
                if (updated.isEmpty()) {
                    return null; // orphaned -> evict
                }
                return new CachedToken(k, token.token(), token.expiresAt(), token.createdAt(), updated);
            });
        }
    }

    @PreDestroy
    void clear() {
        tokens.clear();
        instanceTokenLinks.clear();
    }

    public boolean contains(TokenCacheKey key) {
        return get(key).isPresent();
    }
}
