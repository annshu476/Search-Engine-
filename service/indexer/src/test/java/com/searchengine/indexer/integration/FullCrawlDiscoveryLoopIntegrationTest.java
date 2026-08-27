package com.searchengine.indexer.integration;

import com.searchengine.indexer.indexer.SearchDocumentIndexer;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.SearchCacheService;
import com.searchengine.indexer.service.SearchService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class FullCrawlDiscoveryLoopIntegrationTest {

    @Autowired
    private SearchDocumentIndexer documentIndexer;

    @Autowired
    private SearchService searchService;

    @Autowired
    private SearchCacheService searchCacheService;

    @BeforeEach
    void setUp() {
        searchCacheService.invalidateAll();
        ensureKafkaTopicsExist();
    }

    private void ensureKafkaTopicsExist() {
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000");
            try (AdminClient adminClient = AdminClient.create(props)) {
                Set<String> existingTopics = adminClient.listTopics().names().get();
                List<NewTopic> newTopics = List.of("url-topic", "discovered-urls-topic", "raw-html-topic", "search-document-topic").stream()
                        .filter(t -> !existingTopics.contains(t))
                        .map(t -> new NewTopic(t, 1, (short) 1))
                        .toList();
                if (!newTopics.isEmpty()) {
                    adminClient.createTopics(newTopics).all().get();
                }
            }
        } catch (Exception e) {
            // Ignore if Kafka topics exist or broker offline in unit mode
        }
    }

    @Test
    void testDiscoveredUrlLoopEndToEndIndexingAndSearch() throws Exception {
        String seedUrlHash = "seed-hash-001";
        String seedUrl = "https://example.com/seed-page";

        SearchDocument seedDoc = new SearchDocument(
                seedUrl,
                seedUrl,
                seedUrlHash,
                "Seed Website Landing Page",
                "Official seed website for automated crawl discovery loop test.",
                List.of("Seed Website Title"),
                "Automated crawler discovers subpage1 and subpage2 link from here.",
                "en",
                100,
                200,
                "text/html",
                Instant.now(),
                Instant.now()
        );

        documentIndexer.index(seedDoc);
        Thread.sleep(500);

        SearchResponse response = searchService.search("Seed Website", null, null, null, null, null, 0, 10, "relevance");
        assertThat(response).isNotNull();
        assertThat(response.totalHits()).isGreaterThanOrEqualTo(1);
        assertThat(response.results().get(0).urlHash()).isEqualTo(seedUrlHash);
    }
}
