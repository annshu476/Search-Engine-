package com.searchengine.urlfrontier.service;

import com.searchengine.urlfrontier.hasher.Sha256UrlHasher;
import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.normalizer.UrlNormalizer;
import com.searchengine.urlfrontier.priority.UrlPriorityAssigner;
import com.searchengine.urlfrontier.repository.VisitedUrlRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates URL normalization, hashing, and deduplication for frontier submissions. */
@Service
public class UrlFrontierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlFrontierService.class);
    private final Clock clock;
    private final Sha256UrlHasher sha256UrlHasher;
    private final UrlNormalizer urlNormalizer;
    private final VisitedUrlRepository visitedUrlRepository;
    private final UrlPriorityAssigner urlPriorityAssigner;

    public UrlFrontierService(Clock clock, Sha256UrlHasher sha256UrlHasher, UrlNormalizer urlNormalizer,
                              VisitedUrlRepository visitedUrlRepository, UrlPriorityAssigner urlPriorityAssigner) {
        this.clock = clock;
        this.sha256UrlHasher = sha256UrlHasher;
        this.urlNormalizer = urlNormalizer;
        this.visitedUrlRepository = visitedUrlRepository;
        this.urlPriorityAssigner = urlPriorityAssigner;
    }

    public SubmitUrlResponse submit(SubmitUrlRequest request) {
        String normalizedUrl = urlNormalizer.normalize(request.url());
        String urlHash = sha256UrlHasher.hash(normalizedUrl);
        Instant timestamp = Instant.now(clock);
        boolean accepted = visitedUrlRepository.storeIfAbsent(urlHash, timestamp);
        Integer priority = accepted ? urlPriorityAssigner.assign() : null;
        if (accepted) {
            LOGGER.info("URL_ACCEPTED urlHash={} priority={}", urlHash, priority);
        } else {
            LOGGER.info("URL_DUPLICATE urlHash={}", urlHash);
        }
        return new SubmitUrlResponse(accepted, request.url(), normalizedUrl, urlHash,
                priority,
                accepted ? "URL accepted" : "URL has already been seen", timestamp);
    }
}
