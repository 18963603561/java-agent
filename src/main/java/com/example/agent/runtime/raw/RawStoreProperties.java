package com.example.agent.runtime.raw;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 原始结果存储配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime.raw")
public class RawStoreProperties {

    /**
     * 存储模式。
     *
     * <p>可选值：redis、size-aware、mem。
     */
    private String storeMode = "redis";

    /**
     * Redis 键前缀。
     */
    private String redisKeyPrefix = "raw";

    /**
     * Redis 过期时间（秒）。
     */
    private long redisTtlSeconds = 604800;

    /**
     * 阈值模式下的内存上限（字节）。
     */
    private long sizeAwareThresholdBytes = 1024 * 1024;

    /**
     * 文件落盘目录。
     */
    private String fileBaseDir = "./data/raw";

    /**
     * 文件访问基础 URL。
     */
    private String filePublicBaseUrl = "http://127.0.0.1:8080/api/v1/raw/files";

    public String getStoreMode() {
        return storeMode;
    }

    public void setStoreMode(String storeMode) {
        this.storeMode = storeMode;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public long getRedisTtlSeconds() {
        return redisTtlSeconds;
    }

    public void setRedisTtlSeconds(long redisTtlSeconds) {
        this.redisTtlSeconds = redisTtlSeconds;
    }

    public long getSizeAwareThresholdBytes() {
        return sizeAwareThresholdBytes;
    }

    public void setSizeAwareThresholdBytes(long sizeAwareThresholdBytes) {
        this.sizeAwareThresholdBytes = sizeAwareThresholdBytes;
    }

    public String getFileBaseDir() {
        return fileBaseDir;
    }

    public void setFileBaseDir(String fileBaseDir) {
        this.fileBaseDir = fileBaseDir;
    }

    public String getFilePublicBaseUrl() {
        return filePublicBaseUrl;
    }

    public void setFilePublicBaseUrl(String filePublicBaseUrl) {
        this.filePublicBaseUrl = filePublicBaseUrl;
    }
}

