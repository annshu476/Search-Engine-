package com.searchengine.indexer.consumer;

import com.searchengine.indexer.model.kafka.SearchDocument;
import com.searchengine.indexer.service.IndexerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"search-document-topic"}
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
class SearchDocumentConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoSpyBean
    private IndexerService indexerService;

    @Test
    void endToEndKafkaConsumptionAndProcessing() throws Exception {
        Instant now = Instant.now();
        SearchDocument document = new SearchDocument(
                "https://searchengine.org/page1",
                "https://searchengine.org/page1",
                "hashEmbeddedKafka999",
                "Embedded Kafka Test Page",
                "Test description for embedded Kafka",
                List.of("Section 1", "Section 2"),
                "Body paragraph text for embedded kafka consumer test",
                "en",
                12,
                200,
                "text/html",
                now,
                now
        );

        kafkaTemplate.send("search-document-topic", document.urlHash(), document).get(10, TimeUnit.SECONDS);

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(indexerService, timeout(10000).times(1)).process(captor.capture());

        SearchDocument captured = captor.getValue();
        assertThat(captured).isNotNull();
        assertThat(captured.urlHash()).isEqualTo("hashEmbeddedKafka999");
        assertThat(captured.url()).isEqualTo("https://searchengine.org/page1");
        assertThat(captured.wordCount()).isEqualTo(12);
        assertThat(captured.statusCode()).isEqualTo(200);
    }
}
