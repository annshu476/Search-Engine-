package com.searchengine.indexer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.searchengine.indexer.model.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "indexer.search.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    private final SearchProperties searchProperties;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        SearchProperties.Redis props = searchProperties.getRedis();
        RedisStandaloneConfiguration redisStandaloneConfig = new RedisStandaloneConfiguration();
        redisStandaloneConfig.setHostName(props.getHost());
        redisStandaloneConfig.setPort(props.getPort());

        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            redisStandaloneConfig.setPassword(props.getPassword());
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(props.getTimeout())
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisStandaloneConfig, clientConfig);
        factory.afterPropertiesSet();
        log.info("REDIS_CONNECTION_AVAILABLE host={} port={} keyPrefix={}", props.getHost(), props.getPort(), props.getKeyPrefix());
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, SearchResponse> searchResponseRedisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<SearchResponse> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, SearchResponse.class);

        RedisTemplate<String, SearchResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
