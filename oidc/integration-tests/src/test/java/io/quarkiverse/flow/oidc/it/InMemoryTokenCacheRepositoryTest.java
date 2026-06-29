package io.quarkiverse.flow.oidc.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.oidc.cache.CachedToken;
import io.quarkiverse.flow.oidc.cache.InMemoryTokenCacheRepository;
import io.quarkiverse.flow.oidc.cache.TokenCacheKey;

class InMemoryTokenCacheRepositoryTest {

    @Test
    @DisplayName("Subject token is hashed in cache key")
    void subject_token_is_hashed_in_cache_key() {
        TokenCacheKey key = TokenCacheKey.from("scheme", "super-secret-token", "aud");
        assertThat(key.subjectTokenHash())
                .doesNotContain("super-secret-token")
                .hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("Store token is retrievable until expiry")
    void stored_token_is_retrievable_until_expiry() {
        InMemoryTokenCacheRepository repo = new InMemoryTokenCacheRepository();
        TokenCacheKey key = TokenCacheKey.from("scheme", "subject", "");
        repo.store(new CachedToken(key, "tok", Instant.now().plusSeconds(60), Instant.now(), Set.of()), "i1");

        assertThat(repo.get(key)).isPresent().get().extracting(CachedToken::token).isEqualTo("tok");

        repo.store(new CachedToken(key, "expired", Instant.now().minusSeconds(1), Instant.now(), Set.of()), "i1");
        assertThat(repo.get(key)).isEmpty();
    }

    @Test
    @DisplayName("Token shared across instances evicted only when last instance unlinks")
    void token_shared_across_instances_evicted_only_when_last_instance_unlinks() {
        InMemoryTokenCacheRepository repo = new InMemoryTokenCacheRepository();
        TokenCacheKey key = TokenCacheKey.from("scheme", "subject", "");
        Instant expiry = Instant.now().plusSeconds(300);

        repo.store(new CachedToken(key, "tok", expiry, Instant.now(), Set.of()), "i1");
        repo.store(new CachedToken(key, "tok", expiry, Instant.now(), Set.of()), "i2");

        repo.unlinkInstance("i1");
        assertThat(repo.contains(key)).as("token kept while i2 still uses it").isTrue();

        repo.unlinkInstance("i2");
        assertThat(repo.contains(key)).as("orphaned token evicted").isFalse();
    }

    @Test
    @DisplayName("Near expiry tokens are reported for refresh")
    void near_expiry_tokens_are_reported_for_refresh() {
        InMemoryTokenCacheRepository repo = new InMemoryTokenCacheRepository();
        TokenCacheKey soon = TokenCacheKey.from("scheme", "soon", "");
        TokenCacheKey later = TokenCacheKey.from("scheme", "later", "");
        repo.store(new CachedToken(soon, "a", Instant.now().plusSeconds(30), Instant.now(), Set.of()), "i1");
        repo.store(new CachedToken(later, "b", Instant.now().plusSeconds(3600), Instant.now(), Set.of()), "i1");

        assertThat(repo.getTokensNearingExpiry(Duration.ofMinutes(5)))
                .extracting(CachedToken::key)
                .containsExactly(soon);
    }
}
