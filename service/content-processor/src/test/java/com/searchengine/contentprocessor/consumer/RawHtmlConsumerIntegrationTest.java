package com.searchengine.contentprocessor.consumer;

import com.searchengine.contentprocessor.model.kafka.RawHtmlDocument;
import com.searchengine.contentprocessor.model.kafka.SearchDocument;
import com.searchengine.contentprocessor.service.ContentProcessorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
    void endToEndKafkaMessageConsumptionAndHtmlParsing() throws Exception {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <title>Integration Page Title</title>
                    <meta name="description" content="Integration test page description.">
                    <link rel="canonical" href="https://example.org/canonical-test">
                </head>
                <body>
                    <h1>Main Integration Heading</h1>
                    <p>Sample body paragraph text for integration testing.</p>
                </body>
                </html>
                """;

        RawHtmlDocument rawDocument = new RawHtmlDocument(
                1,
                "https://example.org/test",
                "https://example.org/test",
                "hashIntegration123",
                200,
                "text/html",
                html,
                Instant.now()
        );

        kafkaTemplate.send("raw-html-topic", "hashIntegration123", rawDocument).get(10, TimeUnit.SECONDS);

        ArgumentCaptor<RawHtmlDocument> captor = ArgumentCaptor.forClass(RawHtmlDocument.class);
        verify(contentProcessorService, timeout(10000).times(1)).process(captor.capture());

        RawHtmlDocument capturedRawDoc = captor.getValue();
        assertThat(capturedRawDoc).isNotNull();
        assertThat(capturedRawDoc.urlHash()).isEqualTo("hashIntegration123");
    }
}
