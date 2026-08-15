package com.searchengine.indexer.indexer;

import com.searchengine.indexer.evaluation.SearchEvaluationService;
import com.searchengine.indexer.initializer.SearchDocumentIndexInitializer;
import com.searchengine.indexer.model.evaluation.EvaluationQuery;
import com.searchengine.indexer.model.evaluation.EvaluationReport;
import com.searchengine.indexer.model.kafka.SearchDocument;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
class SearchRelevanceIntegrationTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private SearchDocumentIndexer searchDocumentIndexer;

    @Autowired
    private SearchDocumentIndexInitializer indexInitializer;

    @Autowired
    private SearchEvaluationService evaluationService;

    @Test
    void executeDeterministicSearchRelevanceEvaluationAndVerifyQualityThresholds() throws Exception {
        // 0. Clean index for deterministic integration test run
        if (elasticsearchClient.indices().exists(e -> e.index("search-documents")).value()) {
            elasticsearchClient.indices().delete(d -> d.index("search-documents"));
        }

        // 1. Initialize index mapping
        indexInitializer.initializeIndex();

        // 2. Index deterministic relevance dataset
        Instant now = Instant.now();

        // Doc Title Spring: Exact title phrase match
        SearchDocument docTitleSpring = new SearchDocument(
                "https://searchengine.org/doc-title-spring", "https://searchengine.org/doc-title-spring", "doc-title-spring",
                "Spring Boot Framework", "Java application framework guide", List.of("Spring Boot"),
                "Java application framework core development guide", "en", 200, 200, "text/html", now, now
        );

        // Doc Heading Spring: Heading match
        SearchDocument docHeadingSpring = new SearchDocument(
                "https://searchengine.org/doc-heading-spring", "https://searchengine.org/doc-heading-spring", "doc-heading-spring",
                "Java Application Guide", "Overview of Java framework tutorial", List.of("Spring Boot Tutorial"),
                "Framework development and configuration tutorial", "en", 200, 200, "text/html", now, now
        );

        // Doc Body Spring: Body text match only
        SearchDocument docBodySpring = new SearchDocument(
                "https://searchengine.org/doc-body-spring", "https://searchengine.org/doc-body-spring", "doc-body-spring",
                "Java Development Guide", "Introduction to enterprise development", List.of("Application Development"),
                "This article discusses Spring Boot framework development in detail", "en", 200, 200, "text/html", now, now
        );

        // Doc Scattered: Scattered tokens (Spring and Boot separately)
        SearchDocument docScattered = new SearchDocument(
                "https://searchengine.org/doc-scattered", "https://searchengine.org/doc-scattered", "doc-scattered",
                "Java Programming", "General technology overview", List.of("Application Development"),
                "Guide covering many technologies including spring architecture and boot initialization separately", "en", 200, 200, "text/html", now, now
        );

        // Doc Unrelated: Machine learning
        SearchDocument docUnrelated = new SearchDocument(
                "https://searchengine.org/doc-unrelated", "https://searchengine.org/doc-unrelated", "doc-unrelated",
                "Python Data Science", "Machine learning overview", List.of("Data Science"),
                "Machine learning and pandas data analysis in Python", "en", 200, 200, "text/html", now, now
        );

        // Doc Java Guide: JDK synonym test doc
        SearchDocument docJavaGuide = new SearchDocument(
                "https://searchengine.org/doc-java-guide", "https://searchengine.org/doc-java-guide", "doc-java-guide",
                "Java Development Kit Guide", "Guide to Java JDK tools", List.of("Java JDK"),
                "Body content about Java Development Kit tools", "en", 200, 200, "text/html", now, now
        );

        // Doc XML Spring: Excluded term test doc
        SearchDocument docXmlSpring = new SearchDocument(
                "https://searchengine.org/doc-xml-spring", "https://searchengine.org/doc-xml-spring", "doc-xml-spring",
                "Spring Boot XML Configuration", "Overview of Spring XML beans", List.of("Spring XML"),
                "Spring Boot configuration using legacy XML beans", "en", 200, 200, "text/html", now, now
        );

        searchDocumentIndexer.index(docTitleSpring);
        searchDocumentIndexer.index(docHeadingSpring);
        searchDocumentIndexer.index(docBodySpring);
        searchDocumentIndexer.index(docScattered);
        searchDocumentIndexer.index(docUnrelated);
        searchDocumentIndexer.index(docJavaGuide);
        searchDocumentIndexer.index(docXmlSpring);

        elasticsearchClient.indices().refresh(r -> r.index("search-documents"));

        // 3. Define deterministic evaluation queries
        List<EvaluationQuery> evalQueries = List.of(
                new EvaluationQuery("spring boot", List.of("doc-title-spring"), List.of("doc-heading-spring"), List.of("doc-unrelated"), 1, "Test 1: Exact title phrase outranks heading/body"),
                new EvaluationQuery("spring", List.of("doc-title-spring", "doc-heading-spring"), List.of("doc-body-spring"), List.of("doc-unrelated"), 1, "Test 2: Title & Heading match outrank body match"),
                new EvaluationQuery("sprng boot", List.of("doc-title-spring"), List.of("doc-heading-spring"), List.of("doc-unrelated"), 1, "Test 3: Fuzzy typo tolerance retrieves Spring Boot"),
                new EvaluationQuery("jdk", List.of("doc-java-guide"), List.of("doc-title-spring"), List.of("doc-unrelated"), 1, "Test 4: Synonym JDK retrieves Java document"),
                new EvaluationQuery("spring -xml", List.of("doc-title-spring"), List.of("doc-heading-spring"), List.of("doc-xml-spring"), 1, "Test 5: Excluded term eliminates XML document")
        );

        // 4. Run search quality evaluation
        EvaluationReport report = evaluationService.evaluate(evalQueries);

        // 5. Assert quality thresholds
        assertThat(report.totalQueries()).isEqualTo(5);
        assertThat(report.successfulQueries()).isEqualTo(5);
        assertThat(report.mrr()).isGreaterThanOrEqualTo(0.80);
        assertThat(report.hitAt1()).isGreaterThanOrEqualTo(0.70);
        assertThat(report.hitAt3()).isGreaterThanOrEqualTo(0.90);
        assertThat(report.zeroResultRate()).isLessThanOrEqualTo(0.10);
        assertThat(report.passedThresholds()).isTrue();
    }
}
