package com.example.agent.runtime.raw;

/**
 * 原始结果引用。
 */
public class RawRef {

    /**
     * 存储类型。
     */
    private String store;

    /**
     * 存储键。
     */
    private String key;

    /**
     * 大小。
     */
    private Long size;

    /**
     * 媒体类型。
     */
    private String mediaType;

    /**
     * 创建时间。
     */
    private String createdAt;

    /**
     * 校验哈希。
     */
    private String hash;

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
