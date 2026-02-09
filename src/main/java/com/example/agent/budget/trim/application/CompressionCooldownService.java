package com.example.agent.budget.trim.application;

/**
 * 压缩冷却服务，负责防抖判断与时间戳回填。
 */
public interface CompressionCooldownService {

    /**
     * 判断给定键是否处于冷却中。
     *
     * @param key 冷却键
     * @return 是否处于冷却中
     */
    boolean isInCooldown(String key);

    /**
     * 记录一次压缩触发时间。
     *
     * @param key 冷却键
     */
    void markCompressed(String key);
}
