package com.searchengine.indexer.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.searchengine.indexer.config.SearchProperties;
import com.searchengine.indexer.model.dto.SearchResponse;
import com.searchengine.indexer.model.search.SearchCacheKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchCacheService {

    private final SearchProperties searchProperties;
    private final MeterRegistry meterRegistry;

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

        SearchResponse response = cache.getIfPresent(key);
        if (response != null) {
            meterRegistry.counter("search.cache.hit", "operation", "search").increment();
            log.info("SEARCH_CACHE_HIT page={} size={} sort={}", key.page(), key.size(), key.sort());
            return response;
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
    }

    public void invalidateAll() {
        if (!isCacheEnabled()) {
            return;
        }

        cache.invalidateAll();
        log.info("SEARCH_CACHE_INVALIDATED");
    }

    public long size() {
        return isCacheEnabled() ? cache.estimatedSize() : 0L;
    }
}
