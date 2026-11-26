package io.quarkiverse.flow.runner.security.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.FlowRunnerConfig;

@DisplayName("TokenReview result cache tests")
class TokenReviewCacheTest {

    private static final int MAX_ENTRIES = 16;

    private TokenReviewCache cache;

    @BeforeEach
    void setUp() {
        FlowRunnerConfig config = mock(FlowRunnerConfig.class);
        FlowRunnerConfig.Security security = mock(FlowRunnerConfig.Security.class);
        FlowRunnerConfig.Security.Service service = mock(FlowRunnerConfig.Security.Service.class);
        FlowRunnerConfig.Security.Service.Kubernetes kubernetes = mock(
                FlowRunnerConfig.Security.Service.Kubernetes.class);

        when(config.security()).thenReturn(security);
        when(security.service()).thenReturn(service);
        when(service.kubernetes()).thenReturn(kubernetes);
        when(kubernetes.cacheMaxEntries()).thenReturn(MAX_ENTRIES);

        cache = new TokenReviewCache(config);
    }

    private static TokenReviewClient.TokenReviewResult authenticated(String username, Instant expiresAt) {
        return new TokenReviewClient.TokenReviewResult(true, username, expiresAt);
    }

    @Test
    @DisplayName("test_put_get_roundtrip_returns_cached_result")
    void testPutGetRoundtrip() {
        TokenReviewClient.TokenReviewResult result = authenticated(
                "system:serviceaccount:infrans:gateway",
                Instant.now().plusSeconds(3600));
        cache.put("token-1", result, result.expiresAt());

        Optional<TokenReviewClient.TokenReviewResult> cached = cache.get("token-1");

        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(result);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("test_entry_past_expiry_returns_empty")
    void testEntryPastExpiryReturnsEmpty() {
        TokenReviewClient.TokenReviewResult result = authenticated(
                "system:serviceaccount:infrans:gateway",
                Instant.now().minusSeconds(1));
        cache.put("stale-token", result, result.expiresAt());

        assertThat(cache.get("stale-token")).isEmpty();
    }

    @Test
    @DisplayName("test_negative_result_cached_with_one_minute_ttl")
    void testNegativeResultCachedWithOneMinuteTtl() {
        TokenReviewClient.TokenReviewResult negative = new TokenReviewClient.TokenReviewResult(
                false, null, null);
        // Negative results ignore the caller-supplied expiry (which is null here)
        cache.put("bad-token", negative, null);

        assertThat(cache.get("bad-token")).isPresent();
        assertThat(cache.get("bad-token").get()).isEqualTo(negative);

        // A negative result inserted with an already-past caller expiry is still
        // served within its own 1-minute TTL.
        cache.put("bad-token-2", negative, Instant.now().minusSeconds(1));
        assertThat(cache.get("bad-token-2")).isPresent();
    }

    @Test
    @DisplayName("test_missing_expires_at_falls_back_to_five_minutes")
    void testMissingExpiresAtFallsBackToFiveMinutes() {
        TokenReviewClient.TokenReviewResult result = authenticated(
                "system:serviceaccount:infrans:gateway", null);
        cache.put("no-expiry-token", result, null);

        assertThat(cache.get("no-expiry-token")).isPresent();
    }

    @Test
    @DisplayName("test_cache_evicts_to_max_entries_lru")
    void testCacheEvictsToMaxEntriesLru() {
        for (int i = 0; i < MAX_ENTRIES + 10; i++) {
            TokenReviewClient.TokenReviewResult result = authenticated(
                    "user-" + i, Instant.now().plusSeconds(3600));
            cache.put("token-" + i, result, result.expiresAt());
        }

        assertThat(cache.size()).isEqualTo(MAX_ENTRIES);

        // Least recently used entries were evicted; the most recent insert survives.
        assertThat(cache.get("token-0")).isEmpty();
        assertThat(cache.get("token-" + (MAX_ENTRIES + 9))).isPresent();

        // Accessing the least recently used entry makes it most recently
        // used, so it now survives while its (unaccessed) neighbor is evicted.
        assertThat(cache.get("token-10")).isPresent();
        for (int i = 0; i < 6; i++) {
            TokenReviewClient.TokenReviewResult result = authenticated(
                    "user-b-" + i, Instant.now().plusSeconds(3600));
            cache.put("token-b-" + i, result, result.expiresAt());
        }
        assertThat(cache.get("token-10")).isPresent();
        assertThat(cache.get("token-11")).isEmpty();
    }

    @Test
    @DisplayName("test_keys_are_sha256_hashes_not_raw_tokens")
    void testKeysAreSha256HashesNotRawTokens() {
        String rawToken = "super-secret-raw-token-value";
        String key = cache.keyFor(rawToken);

        // SHA-256 hex encoding: 64 characters
        assertThat(key).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(key).doesNotContain(rawToken);
        assertThat(cache.keySet()).noneMatch(k -> k.contains(rawToken));

        TokenReviewClient.TokenReviewResult result = authenticated(
                "system:serviceaccount:infrans:gateway",
                Instant.now().plusSeconds(3600));
        cache.put(rawToken, result, result.expiresAt());
        assertThat(cache.keySet()).containsExactly(key);
    }

    @Test
    @DisplayName("test_get_unknown_token_returns_empty")
    void testGetUnknownTokenReturnsEmpty() {
        assertThat(cache.get("never-seen")).isEmpty();
        assertThat(cache.size()).isZero();
    }
}
