package com.searchengine.urlfrontier.hasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Generates deterministic SHA-256 hashes for normalized URLs. */
@Component
public class Sha256UrlHasher {

    public String hash(String normalizedUrl) {
        if (normalizedUrl == null || normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("Normalized URL must not be null or blank");
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
