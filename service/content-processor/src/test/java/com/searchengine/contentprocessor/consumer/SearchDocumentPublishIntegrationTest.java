package com.searchengine.contentprocessor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"raw-html-topic", "search-document-topic"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "management.health.kafka.enabled=false"
})
class SearchDocumentPublishIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String RAW_HTML_TOPIC = "raw-html-topic";
    private static final String SEARCH_DOCUMENT_TOPIC = "search-document-topic";
    private static final String HASH = "feature3-integration-hash-999";

    @Test
    void endToEndFlowConsumesRawHtmlAndPublishesSearchDocumentToKafka() throws Exception {
        Consumer<String, String> testConsumer = createTestConsumer(SEARCH_DOCUMENT_TOPIC);

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <title>Integration Pipeline Page</title>
                    <meta name="description" content="Integration pipeline description.">
                    <link rel="canonical" href="https://example.org/pipeline-canonical">
                </head>
                <body>
                    <h1>Pipeline Heading</h1>
                    <p>End to end content processor integration test body.</p>
                </body>
                </html>
                """;

        Instant fetchedAt = Instant.parse("2026-08-13T12:00:00Z");
        RawHtmlDocument rawDoc = new RawHtmlDocument(
                1,
                "https://example.org/pipeline",
                "https://example.org/pipeline",
                HASH,
                200,
                "text/html",
                html,
                fetchedAt
        );

        kafkaTemplate.send(RAW_HTML_TOPIC, HASH, rawDoc).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(testConsumer, SEARCH_DOCUMENT_TOPIC, Duration.ofSeconds(15));
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(HASH);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        SearchDocument publishedDoc = objectMapper.readValue(record.value(), SearchDocument.class);

        assertThat(publishedDoc.url()).isEqualTo("https://example.org/pipeline");
        assertThat(publishedDoc.canonicalUrl()).isEqualTo("https://example.org/pipeline-canonical");
        assertThat(publishedDoc.urlHash()).isEqualTo(HASH);
        assertThat(publishedDoc.title()).isEqualTo("Integration Pipeline Page");
        assertThat(publishedDoc.metaDescription()).isEqualTo("Integration pipeline description.");
        assertThat(publishedDoc.headings()).containsExactly("Pipeline Heading");
        assertThat(publishedDoc.bodyText()).contains("Pipeline Heading End to end content processor integration test body.");
        assertThat(publishedDoc.language()).isEqualTo("en");
        assertThat(publishedDoc.wordCount()).isGreaterThan(0);
        assertThat(publishedDoc.statusCode()).isEqualTo(200);
        assertThat(publishedDoc.contentType()).isEqualTo("text/html");
        assertThat(publishedDoc.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(publishedDoc.indexedAt()).isNotNull();

        testConsumer.close();
    }

    private Consumer<String, String> createTestConsumer(String topic) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-search-doc-group-" + System.currentTimeMillis(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic);
        KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
        return consumer;
    }
}
