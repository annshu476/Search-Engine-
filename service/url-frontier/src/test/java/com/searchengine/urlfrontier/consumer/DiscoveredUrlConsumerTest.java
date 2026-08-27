package com.searchengine.urlfrontier.consumer;

import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.model.kafka.DiscoveredUrl;
import com.searchengine.urlfrontier.service.UrlFrontierService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoveredUrlConsumerTest {

    @Mock
    private UrlFrontierService urlFrontierService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private DiscoveredUrlConsumer consumer;

    @Test
    void consume_validDiscoveredUrl_submitsToServiceAndAcknowledges() {
        DiscoveredUrl payload = new DiscoveredUrl("https://example.com/about", "https://example.com", Instant.now());
        ConsumerRecord<String, DiscoveredUrl> record = new ConsumerRecord<>("discovered-urls-topic", 0, 0L, "key", payload);
        SubmitUrlResponse response = new SubmitUrlResponse(true, "https://example.com/about", "https://example.com/about", "hash123", 5, "URL accepted", Instant.now());

        when(urlFrontierService.submit(any(SubmitUrlRequest.class))).thenReturn(response);

        consumer.consume(record, acknowledgment);

        verify(urlFrontierService, times(1)).submit(new SubmitUrlRequest("https://example.com/about"));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void consume_duplicateDiscoveredUrl_refusedByRedis_handlesSafelyAndAcknowledges() {
        DiscoveredUrl payload = new DiscoveredUrl("https://example.com/visited", "https://example.com", Instant.now());
        ConsumerRecord<String, DiscoveredUrl> record = new ConsumerRecord<>("discovered-urls-topic", 0, 0L, "key", payload);
        SubmitUrlResponse response = new SubmitUrlResponse(false, "https://example.com/visited", "https://example.com/visited", "hashDup", null, "URL has already been seen", Instant.now());

        when(urlFrontierService.submit(any(SubmitUrlRequest.class))).thenReturn(response);

        consumer.consume(record, acknowledgment);

        verify(urlFrontierService, times(1)).submit(new SubmitUrlRequest("https://example.com/visited"));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void consume_nullRecord_skippedSafely() {
        consumer.consume(null, acknowledgment);

        verify(urlFrontierService, never()).submit(any());
        verify(acknowledgment, times(1)).acknowledge();
    }
}
