package com.example.agent.agentcore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 工具缓存，用于存放工具元数据或实例。
 */
@Component
public class ToolCache {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    /**
     * 获取缓存对象。
     *
     * @param key 缓存键
     * @return 缓存对象
     */
    public Object get(String key) {
        return cache.get(key);
    }

    /**
     * 写入缓存对象。
     *
     * @param key 缓存键
     * @param value 缓存对象
     */
    public void put(String key, Object value) {
        cache.put(key, value);
    }
}
