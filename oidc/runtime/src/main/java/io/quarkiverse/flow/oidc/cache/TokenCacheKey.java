package io.quarkiverse.flow.oidc.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Composite cache key for an exchanged token. The subject token is stored only as an SHA-256 hash so that
 * raw user tokens never live in the cache key.
 */
public record TokenCacheKey(String authSchemeName, String subjectTokenHash, String audience) {

    public static TokenCacheKey from(String authSchemeName, String subjectToken, String audience) {
        return new TokenCacheKey(authSchemeName, sha256(subjectToken), audience == null ? "" : audience);
    }

    private static String sha256(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
