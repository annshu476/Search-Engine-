package com.searchengine.crawler.service;

import com.searchengine.crawler.model.kafka.UrlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service boundary for accepted crawl tasks.
 */
@Service
public class CrawlerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlerService.class);

    public void processUrlTask(UrlTask task) {
        LOGGER.info("CRAWLER_SERVICE_RECEIVED urlHash={} url={} priority={}",
                task.urlHash(), task.url(), task.priority());
    }
}
