package com.example.agent.capabilities.tools.execution.service;

import com.example.agent.capabilities.tools.registry.ToolCache;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工具执行缓存服务。
 *
 * <p>用途：封装工具执行缓存读写职责，避免执行器直接操作缓存细节。</p>
 */
@Component
public class ToolExecutionCacheService {

    /**
     * 从缓存读取执行结果。
     *
     * @param toolCache 缓存组件
     * @param cacheKey 缓存键
     * @param ttl 缓存有效期
     * @return 命中结果，未命中返回空
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedResult(ToolCache toolCache, String cacheKey, Duration ttl) {
        if (toolCache == null || cacheKey == null || ttl == null) {
            return null;
        }
        Object cached = toolCache.getIfFresh(cacheKey, ttl);
        if (cached instanceof Map<?, ?> cachedMap) {
            return (Map<String, Object>) cachedMap;
        }
        return null;
    }

    /**
     * 写入执行结果到缓存。
     *
     * @param toolCache 缓存组件
     * @param cacheKey 缓存键
     * @param value 缓存值
     * @param ttl 缓存有效期
     */
    public void putCachedResult(ToolCache toolCache, String cacheKey, Map<String, Object> value, Duration ttl) {
        if (toolCache == null || cacheKey == null || value == null || ttl == null) {
            return;
        }
        toolCache.put(cacheKey, value, ttl);
    }
}

