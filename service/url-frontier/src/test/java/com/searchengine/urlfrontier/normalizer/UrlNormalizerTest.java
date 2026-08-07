package com.searchengine.urlfrontier.normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    private final UrlNormalizer urlNormalizer = new UrlNormalizer();

    @Test
    void trimsWhitespaceAndLowercasesSchemeAndHost() {
        assertThat(urlNormalizer.normalize(" HTTPS://SPRING.IO "))
                .isEqualTo("https://spring.io");
    }

    @Test
    void removesTrailingSlashOnlyForRootPath() {
        assertThat(urlNormalizer.normalize("https://spring.io/"))
                .isEqualTo("https://spring.io");
        assertThat(urlNormalizer.normalize("https://spring.io/docs/"))
                .isEqualTo("https://spring.io/docs/");
    }

    @Test
    void preservesQueryParametersAndFragments() {
        assertThat(urlNormalizer.normalize("https://spring.io/docs?page=2#overview"))
                .isEqualTo("https://spring.io/docs?page=2#overview");
    }

    @Test
    void rejectsInvalidUrl() {
        assertThatThrownBy(() -> urlNormalizer.normalize("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
