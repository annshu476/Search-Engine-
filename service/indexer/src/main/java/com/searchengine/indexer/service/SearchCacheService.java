package com.searchengine.indexer.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.searchengine.indexer.cache.RedisSearchCache;
import com.searchengine.indexer.cache.SearchCacheInvalidationPublisher;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.search.SearchCacheKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchCacheService {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private RedisSearchCache redisSearchCache;

    @Autowired(required = false)
    private SearchCacheInvalidationPublisher invalidationPublisher;

    private Cache<SearchCacheKey, SearchResponse> cache;

    @PostConstruct
    public void init() {
        if (searchProperties.getCache().isEnabled()) {
            this.cache = Caffeine.newBuilder()
                    .maximumSize(searchProperties.getCache().getMaximumSize())
                    .expireAfterWrite(searchProperties.getCache().getTtl())
                    .recordStats()
                    .build();

            try {
                CaffeineCacheMetrics.monitor(meterRegistry, this.cache, "search.cache");
            } catch (Exception e) {
                log.debug("CaffeineCacheMetrics binding skipped: {}", e.getMessage());
            }

            log.info("SEARCH_CACHE_INITIALIZED enabled=true maximumSize={} ttl={}",
                    searchProperties.getCache().getMaximumSize(), searchProperties.getCache().getTtl());
        } else {
            log.info("SEARCH_CACHE_INITIALIZED enabled=false");
        }
    }

    public boolean isCacheEnabled() {
        return searchProperties.getCache().isEnabled() && cache != null;
    }

    public String getConfigVersion() {
        SearchProperties.Relevance r = searchProperties.getRelevance();
        SearchProperties.Fuzzy f = searchProperties.getFuzzy();
        SearchProperties.Highlight h = searchProperties.getHighlight();
        SearchProperties.Synonyms syn = searchProperties.getSynonyms();
        SearchProperties.SpellCorrection sc = searchProperties.getSpellCorrection();
        return "v2:" + r.getTitleBoost() + ":" + r.getHeadingsBoost() + ":" + r.getMetaDescriptionBoost()
                + ":" + r.getBodyBoost() + ":" + r.getPhraseBoost() + ":" + r.getTitlePhraseBoost()
                + ":" + f.isEnabled() + ":" + f.getFuzziness()
                + ":" + h.isEnabled() + ":" + h.getFragmentSize() + ":" + h.getNumberOfFragments()
                + ":" + syn.isEnabled() + ":" + syn.getMaximumSynonymsPerTerm() + ":" + (syn.getRules() != null ? syn.getRules().hashCode() : 0)
                + ":" + sc.isEnabled() + ":" + sc.getMaximumSuggestions() + ":" + sc.getMinimumTermLength();
    }

    public SearchResponse get(SearchCacheKey key) {
        if (!isCacheEnabled() || key == null) {
            return null;
        }

        // L1 Caffeine Cache
        SearchResponse response = cache.getIfPresent(key);
        if (response != null) {
            meterRegistry.counter("search.cache.hit", "operation", "search").increment();
            log.info("SEARCH_CACHE_HIT page={} size={} sort={}", key.page(), key.size(), key.sort());
            return response;
        }

        // L2 Redis Cache
        if (redisSearchCache != null) {
            SearchResponse redisResponse = redisSearchCache.get(key);
            if (redisResponse != null) {
                cache.put(key, redisResponse); // Populate L1 Caffeine
                meterRegistry.counter("search.cache.hit", "operation", "search").increment();
                log.info("REDIS_CACHE_HIT page={} size={} sort={}", key.page(), key.size(), key.sort());
                return redisResponse;
            }
        }

        meterRegistry.counter("search.cache.miss", "operation", "search").increment();
        log.info("SEARCH_CACHE_MISS page={} size={} sort={}", key.page(), key.size(), key.sort());
        return null;
    }

    public void put(SearchCacheKey key, SearchResponse response) {
        if (!isCacheEnabled() || key == null || response == null) {
            return;
        }

        cache.put(key, response);
        meterRegistry.counter("search.cache.put", "operation", "search").increment();
        log.info("SEARCH_CACHE_PUT page={} size={} sort={}", key.page(), key.size(), key.sort());

        if (redisSearchCache != null) {
            redisSearchCache.put(key, response);
        }
    }

    public void invalidateAll() {
        clearLocalCache();
        if (redisSearchCache != null) {
            redisSearchCache.clear();
        }
        if (invalidationPublisher != null) {
            invalidationPublisher.publishInvalidation();
        }
    }

    public void clearLocalCache() {
        if (isCacheEnabled()) {
            cache.invalidateAll();
            log.info("SEARCH_CACHE_INVALIDATED");
        }
    }

    public long size() {
        return isCacheEnabled() ? cache.estimatedSize() : 0L;
    }
}
