package com.searchengine.contentprocessor.consumer;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.service.ContentProcessorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"raw-html-topic"}
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
class RawHtmlConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @SpyBean
    private ContentProcessorService contentProcessorService;

    @Test
    void endToEndKafkaMessageConsumption() throws Exception {
        RawHtmlDocument document = new RawHtmlDocument(
                1,
                "https://example.org/test",
                "https://example.org/test",
                "hash999",
                200,
                "text/html",
                "<html><body>Integration Test</body></html>",
                Instant.now()
        );

        kafkaTemplate.send("raw-html-topic", "hash999", document).get(10, TimeUnit.SECONDS);

        verify(contentProcessorService, timeout(10000).times(1)).process(any(RawHtmlDocument.class));
    }
}
