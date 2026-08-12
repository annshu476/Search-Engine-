package com.searchengine.crawler.service;

import com.searchengine.crawler.exception.PageFetchException;
import com.searchengine.crawler.exception.RobotsUnavailableException;
import com.searchengine.crawler.fetcher.PageFetcher;
import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.model.dto.RobotsCheckResult;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.robots.RobotsChecker;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service coordinating robots compliance and URL page fetching.
 */
@Service
public class CrawlerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlerService.class);
    private static final Duration ROBOTS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

    private final RobotsChecker robotsChecker;
    private final PageFetcher pageFetcher;

    public CrawlerService(RobotsChecker robotsChecker, PageFetcher pageFetcher) {
        this.robotsChecker = robotsChecker;
        this.pageFetcher = pageFetcher;
    }

    public void processUrlTask(UrlTask task) {
        LOGGER.info("CRAWLER_SERVICE_RECEIVED urlHash={} url={} priority={}",
                task.urlHash(), task.url(), task.priority());

        RobotsCheckResult robotsResult = robotsChecker.check(task.url()).block(ROBOTS_TIMEOUT);

        if (robotsResult == null || !robotsResult.robotsAvailable()) {
            String failureReason = robotsResult != null ? robotsResult.reason() : "Robots check timed out";
            LOGGER.error("ROBOTS_UNAVAILABLE urlHash={} url={} reason=\"{}\"",
                    task.urlHash(), task.url(), failureReason);
            throw new RobotsUnavailableException(task.url(), failureReason);
        }

        if (!robotsResult.allowed()) {
            LOGGER.info("ROBOTS_DISALLOWED urlHash={} url={} reason=\"{}\"",
                    task.urlHash(), task.url(), robotsResult.reason());
            return;
        }

        LOGGER.info("ROBOTS_ALLOWED urlHash={} url={} reason=\"{}\"",
                task.urlHash(), task.url(), robotsResult.reason());

        PageFetchResult result = pageFetcher.fetch(task.url()).block(FETCH_TIMEOUT);

        if (result == null || !result.success()) {
            int statusCode = result != null ? result.statusCode() : 0;
            String failureReason = (result != null && result.failureReason() != null) ? result.failureReason() : "Unknown fetch failure";
            LOGGER.error("PAGE_FETCH_FAILURE urlHash={} requestedUrl={} statusCode={} failureReason={}",
                    task.urlHash(), task.url(), statusCode, failureReason);
            PageFetchResult failedResult = result != null ? result : PageFetchResult.failure(task.url(), task.url(), statusCode, null, failureReason, Instant.now());
            throw new PageFetchException(failedResult);
        }

        int responseSize = result.body() != null ? result.body().length() : 0;
        LOGGER.info("PAGE_FETCH_SUCCESS urlHash={} requestedUrl={} finalUrl={} statusCode={} contentType={} responseSize={}",
                task.urlHash(), result.requestedUrl(), result.finalUrl(), result.statusCode(), result.contentType(), responseSize);
    }
}
