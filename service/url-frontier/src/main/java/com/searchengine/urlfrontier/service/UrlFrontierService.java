package com.searchengine.urlfrontier.service;

import com.searchengine.urlfrontier.hasher.Sha256UrlHasher;
import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.normalizer.UrlNormalizer;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Coordinates URL normalization and hash generation for frontier submissions. */
@Service
public class UrlFrontierService {

    private final Clock clock;
    private final Sha256UrlHasher sha256UrlHasher;
    private final UrlNormalizer urlNormalizer;

    public UrlFrontierService(Clock clock, Sha256UrlHasher sha256UrlHasher, UrlNormalizer urlNormalizer) {
        this.clock = clock;
        this.sha256UrlHasher = sha256UrlHasher;
        this.urlNormalizer = urlNormalizer;
    }

    public SubmitUrlResponse submit(SubmitUrlRequest request) {
        String normalizedUrl = urlNormalizer.normalize(request.url());
        String urlHash = sha256UrlHasher.hash(normalizedUrl);

        return new SubmitUrlResponse(
                true,
                request.url(),
                normalizedUrl,
                urlHash,
                "URL processed successfully",
                Instant.now(clock)
        );
    }
}
