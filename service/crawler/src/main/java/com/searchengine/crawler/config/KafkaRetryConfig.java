package com.searchengine.crawler.config;

import com.searchengine.crawler.exception.RetryableCrawlerException;
import com.searchengine.crawler.exception.RobotsUnavailableException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.backoff.SleepingBackOffPolicy;

@Configuration
public class KafkaRetryConfig {

    @Bean
    public RetryTopicConfiguration crawlerRetryTopicConfiguration(KafkaTemplate<String, Object> template) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .customBackoff(new ExplicitSequenceBackOffPolicy(2000L, 5000L, 15000L))
                .maxAttempts(4)
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .dltHandlerMethod("urlTaskConsumer", "handleDlt")
                .dltSuffix("-dlt")
                .retryTopicSuffix("-retry")
                .setTopicSuffixingStrategy(TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE)
                .includeTopic("url-topic")
                .retryOn(List.of(RetryableCrawlerException.class, RobotsUnavailableException.class))
                .create(template);
    }

    public static class ExplicitSequenceBackOffPolicy implements SleepingBackOffPolicy<ExplicitSequenceBackOffPolicy> {

        private final long[] delays;
        private Sleeper sleeper = (backOffPeriod) -> {
            if (backOffPeriod > 0) {
                Thread.sleep(backOffPeriod);
            }
        };

        public ExplicitSequenceBackOffPolicy(long... delays) {
            this.delays = delays;
        }

        @Override
        public ExplicitSequenceBackOffPolicy withSleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        @Override
        public BackOffContext start(RetryContext context) {
            return new SequenceBackOffContext();
        }

        @Override
        public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
            SequenceBackOffContext context = (SequenceBackOffContext) backOffContext;
            int count = context.getAndIncrement();
            long backOffPeriod = count < delays.length ? delays[count] : (delays.length > 0 ? delays[delays.length - 1] : 0L);
            try {
                if (this.sleeper != null) {
                    this.sleeper.sleep(backOffPeriod);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BackOffInterruptedException("Interrupted during backOff", e);
            }
        }

        public static class SequenceBackOffContext implements BackOffContext {
            private int count = 0;

            public int getAndIncrement() {
                return count++;
            }
        }
    }
}
