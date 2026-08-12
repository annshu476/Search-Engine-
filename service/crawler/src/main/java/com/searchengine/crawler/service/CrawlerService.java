package com.searchengine.crawler.service;

import com.searchengine.crawler.exception.PageFetchException;
import com.searchengine.crawler.fetcher.PageFetcher;
import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.model.kafka.UrlTask;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service coordinating URL task execution and page fetching.
 */
@Service
public class CrawlerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlerService.class);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

    private final PageFetcher pageFetcher;

    public CrawlerService(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    public void processUrlTask(UrlTask task) {
        LOGGER.info("CRAWLER_SERVICE_RECEIVED urlHash={} url={} priority={}",
                task.urlHash(), task.url(), task.priority());

        PageFetchResult result = pageFetcher.fetch(task.url()).block(FETCH_TIMEOUT);

        if (result == null || !result.success()) {
            String failureReason = (result != null && result.failureReason() != null) ? result.failureReason() : "Unknown fetch failure";
            LOGGER.error("PAGE_FETCH_FAILURE urlHash={} requestedUrl={} failureReason={}",
                    task.urlHash(), task.url(), failureReason);
            PageFetchResult failedResult = result != null ? result : PageFetchResult.failure(task.url(), task.url(), 0, null, failureReason, Instant.now());
            throw new PageFetchException(failedResult);
        }

        int responseSize = result.body() != null ? result.body().length() : 0;
        LOGGER.info("PAGE_FETCH_SUCCESS urlHash={} requestedUrl={} finalUrl={} statusCode={} contentType={} responseSize={}",
                task.urlHash(), result.requestedUrl(), result.finalUrl(), result.statusCode(), result.contentType(), responseSize);
    }
}
