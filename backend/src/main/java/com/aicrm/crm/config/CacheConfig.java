package com.aicrm.crm.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine in-memory cache 設定，TTL 2 分鐘。
 * 四個 dashboard cache 各自獨立命名，供 @Cacheable / @CacheEvict 使用。
 */
@Configuration
public class CacheConfig {

    /** Dashboard cache 名稱常數，供 @Cacheable / @CacheEvict 引用。 */
    public static final String CACHE_DASHBOARD_SUMMARY   = "dashboard-summary";
    public static final String CACHE_DASHBOARD_REPORTS   = "dashboard-reports";
    public static final String CACHE_DASHBOARD_RFM       = "dashboard-rfm";
    public static final String CACHE_DASHBOARD_SENTIMENT = "dashboard-sentiment";

    /**
     * 建立 Caffeine CacheManager，統一 TTL 2 分鐘。
     * maximumSize=1：dashboard 聚合無分 key，只存一份結果。
     *
     * @return Spring CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(
                CACHE_DASHBOARD_SUMMARY,
                CACHE_DASHBOARD_REPORTS,
                CACHE_DASHBOARD_RFM,
                CACHE_DASHBOARD_SENTIMENT
        ));
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .maximumSize(1));
        return manager;
    }
}
