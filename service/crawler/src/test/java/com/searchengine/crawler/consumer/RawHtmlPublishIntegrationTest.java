package com.searchengine.crawler.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.searchengine.crawler.fetcher.PageFetcher;
import com.searchengine.crawler.model.dto.PageFetchResult;
import com.searchengine.crawler.model.dto.RobotsCheckResult;
import com.searchengine.crawler.model.kafka.RawHtmlDocument;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.robots.RobotsChecker;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"url-topic", "raw-html-topic"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.properties.bootstrap.servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "crawler.retry.max-attempts=4"
})
class RawHtmlPublishIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private RobotsChecker robotsChecker;

    @MockBean
    private PageFetcher pageFetcher;

    private static final String URL_TOPIC = "url-topic";
    private static final String RAW_HTML_TOPIC = "raw-html-topic";
    private static final String HASH = "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28";

    @Test
    void endToEndFlowPublishesRawHtmlDocumentToKafka() throws Exception {
        UrlTask task = new UrlTask(1, "https://spring.io", HASH, 5, Instant.now());

        when(robotsChecker.check(anyString()))
                .thenReturn(Mono.just(RobotsCheckResult.allowed("https://spring.io", -1, "Allowed")));
        when(pageFetcher.fetch(anyString()))
                .thenReturn(Mono.just(PageFetchResult.success(
                        "https://spring.io", "https://spring.io", 200, "text/html", "<html>Integration Test Content</html>", Instant.now())));

        Consumer<String, String> rawHtmlConsumer = createTestConsumer(RAW_HTML_TOPIC);

        kafkaTemplate.send(URL_TOPIC, HASH, task).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(rawHtmlConsumer, RAW_HTML_TOPIC, Duration.ofSeconds(15));
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(HASH);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        RawHtmlDocument publishedDoc = objectMapper.readValue(record.value(), RawHtmlDocument.class);

        assertThat(publishedDoc.schemaVersion()).isEqualTo(1);
        assertThat(publishedDoc.url()).isEqualTo("https://spring.io");
        assertThat(publishedDoc.finalUrl()).isEqualTo("https://spring.io");
        assertThat(publishedDoc.urlHash()).isEqualTo(HASH);
        assertThat(publishedDoc.statusCode()).isEqualTo(200);
        assertThat(publishedDoc.contentType()).isEqualTo("text/html");
        assertThat(publishedDoc.html()).isEqualTo("<html>Integration Test Content</html>");

        rawHtmlConsumer.close();
    }

    private Consumer<String, String> createTestConsumer(String topic) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group-" + System.currentTimeMillis(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic);
        KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
        return consumer;
    }
}
