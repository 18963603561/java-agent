package com.example.agent.governance.common.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * 通用内存状态仓储。
 *
 * @param <T> 记录类型
 */
public class GenericStateStore<T> {

    private final ConcurrentHashMap<String, T> entries = new ConcurrentHashMap<>();
    private final ToLongFunction<T> lastAccessEpochMillisExtractor;
    private final BiConsumer<String, T> onExpired;
    private final StoreMetricsRecorder<T> metricsRecorder;

    private volatile long lastCleanupAtEpochMs = System.currentTimeMillis();

    public GenericStateStore(ToLongFunction<T> lastAccessEpochMillisExtractor,
                             BiConsumer<String, T> onExpired,
                             StoreMetricsRecorder<T> metricsRecorder) {
        this.lastAccessEpochMillisExtractor = lastAccessEpochMillisExtractor;
        this.onExpired = onExpired;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * 写入记录。
     *
     * @param key 主键
     * @param value 记录
     * @param policy 仓储策略
     * @return 是否写入成功
     */
    public boolean put(String key, T value, StateStorePolicy policy) {
        cleanupIfNecessary(policy);
        int currentSize = entries.size();
        if (currentSize >= policy.getMaxSize()) {
            if (metricsRecorder != null) {
                metricsRecorder.onCapacityRejected(value, currentSize, policy.getMaxSize());
            }
            return false;
        }
        entries.put(key, value);
        return true;
    }

    /**
     * 读取或原子创建记录。
     *
     * @param key 主键
     * @param policy 仓储策略
     * @param supplier 创建器
     * @return 已存在或新创建的记录，容量超限时返回空
     */
    public T getOrCreate(String key, StateStorePolicy policy, Supplier<T> supplier) {
        cleanupIfNecessary(policy);
        T existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (entries) {
            existing = entries.get(key);
            if (existing != null) {
                return existing;
            }
            int currentSize = entries.size();
            T created = supplier == null ? null : supplier.get();
            if (created == null) {
                return null;
            }
            if (currentSize >= policy.getMaxSize()) {
                if (metricsRecorder != null) {
                    metricsRecorder.onCapacityRejected(created, currentSize, policy.getMaxSize());
                }
                return null;
            }
            entries.put(key, created);
            return created;
        }
    }

    /**
     * 读取记录。
     *
     * @param key 主键
     * @param policy 仓储策略
     * @return 记录
     */
    public T get(String key, StateStorePolicy policy) {
        cleanupIfNecessary(policy);
        return entries.get(key);
    }

    /**
     * 删除记录。
     *
     * @param key 主键
     */
    public void remove(String key) {
        entries.remove(key);
    }

    /**
     * 返回当前大小。
     *
     * @param policy 仓储策略
     * @return 大小
     */
    public int size(StateStorePolicy policy) {
        cleanupIfNecessary(policy);
        return entries.size();
    }

    /**
     * 查找首个匹配记录的主键。
     *
     * @param policy 仓储策略
     * @param matcher 匹配器
     * @return 主键
     */
    public String findFirstKey(StateStorePolicy policy, StoreEntryMatcher<T> matcher) {
        cleanupIfNecessary(policy);
        if (matcher == null) {
            return null;
        }
        for (Map.Entry<String, T> entry : entries.entrySet()) {
            T value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (matcher.matches(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 按策略执行惰性清理。
     *
     * @param policy 仓储策略
     */
    public void cleanupIfNecessary(StateStorePolicy policy) {
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(1, policy.getCleanupIntervalSeconds()) * 1000L;
        if (now - lastCleanupAtEpochMs < intervalMs) {
            return;
        }
        synchronized (entries) {
            if (now - lastCleanupAtEpochMs < intervalMs) {
                return;
            }
            int removed = cleanupExpired(now, policy);
            lastCleanupAtEpochMs = now;
            if (removed > 0 && metricsRecorder != null) {
                metricsRecorder.onCleanup(removed, entries.size());
            }
        }
    }

    private int cleanupExpired(long nowEpochMs, StateStorePolicy policy) {
        long ttlMs = Math.max(1, policy.getTtlSeconds()) * 1000L;
        int removed = 0;
        for (Map.Entry<String, T> entry : entries.entrySet()) {
            T value = entry.getValue();
            if (value == null) {
                continue;
            }
            long lastAccessEpochMs = Math.max(0, lastAccessEpochMillisExtractor.applyAsLong(value));
            if (lastAccessEpochMs <= 0) {
                continue;
            }
            if (nowEpochMs - lastAccessEpochMs < ttlMs) {
                continue;
            }
            if (entries.remove(entry.getKey(), value)) {
                removed++;
                if (onExpired != null) {
                    onExpired.accept(entry.getKey(), value);
                }
            }
        }
        return removed;
    }
}
