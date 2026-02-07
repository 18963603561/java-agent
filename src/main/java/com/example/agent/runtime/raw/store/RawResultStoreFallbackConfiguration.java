package com.example.agent.runtime.raw.store;

import com.example.agent.runtime.raw.RawStoreProperties;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 原始结果存储兜底配置。
 *
 * <p>用途：当未命中 Redis/阈值/显式内存实现时，提供内存存储兜底，保证运行与测试链路可启动。
 */
@Configuration
public class RawResultStoreFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RawResultStoreFallbackConfiguration.class);

    /**
     * 注册兜底原始结果存储器。
     *
     * @param objectMapper JSON 序列化器
     * @param rawRefCodec 引用编解码器
     * @param properties 原始存储配置
     * @return 内存存储实现
     */
    @Bean
    @ConditionalOnMissingBean({RawResultStore.class, StringRedisTemplate.class})
    @ConditionalOnProperty(prefix = "agent.runtime.raw", name = "store-mode", havingValue = "redis", matchIfMissing = true)
    public RawResultStore fallbackRawResultStore(ObjectMapper objectMapper,
                                                 RawRefCodec rawRefCodec,
                                                 RawStoreProperties properties) {
        log.warn("未检测到可用 RawResultStore 实现，已启用内存兜底存储, configuredMode={}",
                properties != null ? properties.getStoreMode() : null);
        return new InMemoryRawResultStore(objectMapper, rawRefCodec);
    }
}
