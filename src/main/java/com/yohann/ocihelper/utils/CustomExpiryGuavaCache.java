package com.yohann.ocihelper.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.google.common.cache.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * <p>
 * CustomExpiryGuavaCache
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 13:50
 */
@Slf4j
@Component
public class CustomExpiryGuavaCache<K, V> {

    /**
     * 缓存存储的值，包括实际值和过期时间
     *
     * @param <V>
     */
    private static class CacheValue<V> {
        private final V value;
        private final long expiryTime;

        public CacheValue(V value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public V getValue() {
            return value;
        }

        public boolean isExpired(long currentTime) {
            return currentTime > expiryTime;
        }
    }

    private final Cache<K, CacheValue<V>> cache;
    private static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(1,
            ThreadFactoryBuilder.create().setDaemon(true).setNamePrefix("clean-cache-task-").build());

    public CustomExpiryGuavaCache() {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.DAYS) // 全局过期时间，仅用于兜底
                .removalListener((RemovalListener<K, CacheValue<V>>) notification -> {
                    if (notification.wasEvicted()) {
                        log.info("cache key: [{}] was evicted.", notification.getKey());
                    }
                })
                .build();

        // 定期清理过期条目
        SCHEDULER.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            cache.asMap().entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 添加键值对，并设置自定义过期时间（单位：毫秒）
     *
     * @param key
     * @param value
     * @param ttlMillis
     */
    public void put(K key, V value, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        cache.put(key, new CacheValue<>(value, expiryTime));
    }

    /**
     * 获取值，并检查是否过期
     *
     * @param key
     * @return
     */
    public V get(K key) {
        CacheValue<V> cacheValue = cache.getIfPresent(key);
        if (cacheValue == null || cacheValue.isExpired(System.currentTimeMillis())) {
            cache.invalidate(key); // 过期后主动移除
            return null;
        }
        return cacheValue.getValue();
    }

    /**
     * 移除键
     *
     * @param key
     */
    public void remove(K key) {
        cache.invalidate(key);
    }

    /**
     * 清理过期数据
     */
    public void cleanUp() {
        cache.cleanUp();
    }

}