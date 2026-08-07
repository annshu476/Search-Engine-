package com.searchengine.urlfrontier.hasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.searchengine.urlfrontier.normalizer.UrlNormalizer;
import org.junit.jupiter.api.Test;

class Sha256UrlHasherTest {

    private final Sha256UrlHasher sha256UrlHasher = new Sha256UrlHasher();
    private final UrlNormalizer urlNormalizer = new UrlNormalizer();

    @Test
    void returnsSameHashForSameUrl() {
        String normalizedUrl = "https://spring.io";

        assertThat(sha256UrlHasher.hash(normalizedUrl))
                .isEqualTo(sha256UrlHasher.hash(normalizedUrl));
    }

    @Test
    void returnsIdenticalHashesForEquivalentNormalizedUrls() {
        String firstNormalizedUrl = urlNormalizer.normalize("HTTPS://SPRING.IO/");
        String secondNormalizedUrl = urlNormalizer.normalize("https://spring.io");

        assertThat(sha256UrlHasher.hash(firstNormalizedUrl))
                .isEqualTo(sha256UrlHasher.hash(secondNormalizedUrl));
    }

    @Test
    void returnsDifferentHashesForDifferentUrls() {
        assertThat(sha256UrlHasher.hash("https://spring.io"))
                .isNotEqualTo(sha256UrlHasher.hash("https://spring.io/projects"));
    }

    @Test
    void rejectsEmptyNormalizedUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> sha256UrlHasher.hash(""));
        assertThatIllegalArgumentException().isThrownBy(() -> sha256UrlHasher.hash("   "));
    }

    @Test
    void rejectsNullNormalizedUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> sha256UrlHasher.hash(null));
    }
}
