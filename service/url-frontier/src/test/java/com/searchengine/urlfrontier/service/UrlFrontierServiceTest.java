package com.searchengine.urlfrontier.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.searchengine.urlfrontier.hasher.Sha256UrlHasher;
import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.normalizer.UrlNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UrlFrontierServiceTest {

    @Test
    void returnsOriginalAndNormalizedUrlsWithCurrentTimestamp() {
        Instant timestamp = Instant.parse("2026-07-31T10:00:00Z");
        UrlFrontierService service = new UrlFrontierService(
                Clock.fixed(timestamp, ZoneOffset.UTC),
                new Sha256UrlHasher(),
                new UrlNormalizer()
        );

        SubmitUrlResponse response = service.submit(new SubmitUrlRequest(" HTTPS://SPRING.IO/ "));

        assertThat(response.accepted()).isTrue();
        assertThat(response.originalUrl()).isEqualTo(" HTTPS://SPRING.IO/ ");
        assertThat(response.normalizedUrl()).isEqualTo("https://spring.io");
        assertThat(response.urlHash()).isEqualTo("007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28");
        assertThat(response.message()).isEqualTo("URL processed successfully");
        assertThat(response.timestamp()).isEqualTo(timestamp);
    }
}
