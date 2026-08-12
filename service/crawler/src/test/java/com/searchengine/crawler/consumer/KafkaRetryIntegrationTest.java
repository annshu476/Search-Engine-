package com.searchengine.crawler.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.searchengine.crawler.exception.NonRetryableCrawlerException;
import com.searchengine.crawler.exception.RetryableCrawlerException;
import com.searchengine.crawler.model.kafka.UrlTask;
import com.searchengine.crawler.service.CrawlerService;
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

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"url-topic", "url-topic-retry-2000", "url-topic-retry-5000", "url-topic-retry-15000", "url-topic-dlt"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.properties.bootstrap.servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "crawler.retry.max-attempts=4"
})
class KafkaRetryIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private CrawlerService crawlerService;

    private static final String MAIN_TOPIC = "url-topic";
    private static final String DLT_TOPIC = "url-topic-dlt";
    private static final String HASH = "007f61681d94a000cdbe12b4e4bf3ec8ff126d8cb79179a01a79be2caa410b28";

    @Test
    void verifiesGeneratedTopicsInEmbeddedKafka() {
        assertThat(embeddedKafkaBroker.getTopics())
                .contains(MAIN_TOPIC, "url-topic-retry-2000", "url-topic-retry-5000", "url-topic-retry-15000", DLT_TOPIC);
    }

    @Test
    void routesPermanentFailureDirectlyToDlt() throws Exception {
        UrlTask task = new UrlTask(1, "https://example.com/404", HASH, 5, Instant.now());
        doThrow(new NonRetryableCrawlerException(task.url(), "HTTP 404", 404))
                .when(crawlerService).processUrlTask(any());

        Consumer<String, String> dltConsumer = createTestConsumer(DLT_TOPIC);

        kafkaTemplate.send(MAIN_TOPIC, HASH, task).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(15));
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).contains("https://example.com/404");

        dltConsumer.close();
    }

    @Test
    void routesMalformedKafkaPayloadDirectlyToDlt() throws Exception {
        Consumer<String, String> dltConsumer = createTestConsumer(DLT_TOPIC);

        kafkaTemplate.send(MAIN_TOPIC, HASH, "invalid raw payload").get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(15));
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).isNotNull();

        dltConsumer.close();
    }

    @Test
    void verifiesRetryableFailureAttempts() throws Exception {
        UrlTask task = new UrlTask(1, "https://example.com/500", HASH, 5, Instant.now());
        doThrow(new RetryableCrawlerException(task.url(), "HTTP 500", 500))
                .when(crawlerService).processUrlTask(any());

        kafkaTemplate.send(MAIN_TOPIC, HASH, task).get(10, TimeUnit.SECONDS);

        verify(crawlerService, timeout(15000).atLeast(2)).processUrlTask(any());
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
