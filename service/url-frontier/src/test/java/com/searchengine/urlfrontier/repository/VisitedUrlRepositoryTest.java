package com.searchengine.urlfrontier.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.searchengine.urlfrontier.exception.RedisUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class VisitedUrlRepositoryTest {

    @Test
    void storesVisitedUrlWithSingleAtomicSetIfAbsentAndSevenDayTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Instant discoveredAt = Instant.parse("2026-07-31T10:00:00Z");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL)).thenReturn(true);

        boolean stored = new VisitedUrlRepository(redisTemplate).storeIfAbsent("hash", discoveredAt);

        assertThat(stored).isTrue();
        verify(redisTemplate).opsForValue();
        verify(valueOperations).setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL);
        verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    void returnsFalseForDuplicateWithoutAdditionalRedisCalls() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Instant discoveredAt = Instant.parse("2026-07-31T10:00:00Z");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL)).thenReturn(false);

        boolean stored = new VisitedUrlRepository(redisTemplate).storeIfAbsent("hash", discoveredAt);

        assertThat(stored).isFalse();
        verify(redisTemplate).opsForValue();
        verify(valueOperations).setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL);
        verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    void throwsServiceUnavailableWhenRedisReturnsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Instant discoveredAt = Instant.parse("2026-07-31T10:00:00Z");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL)).thenReturn(null);

        assertThatThrownBy(() -> new VisitedUrlRepository(redisTemplate).storeIfAbsent("hash", discoveredAt))
                .isInstanceOf(RedisUnavailableException.class);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL);
        verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    void throwsServiceUnavailableWhenRedisAccessFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Instant discoveredAt = Instant.parse("2026-07-31T10:00:00Z");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThatThrownBy(() -> new VisitedUrlRepository(redisTemplate).storeIfAbsent("hash", discoveredAt))
                .isInstanceOf(RedisUnavailableException.class);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).setIfAbsent("visited:hash", discoveredAt.toString(),
                VisitedUrlRepository.VISITED_URL_TTL);
        verifyNoMoreInteractions(redisTemplate, valueOperations);
    }
}
