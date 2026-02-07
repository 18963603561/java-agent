package com.example.agent.runtime.raw.ref;

/**
 * 原始结果引用对象。
 *
 * <p>用途：描述原始数据的存储位置、引用标识、访问地址和校验信息。
 * <p>边界条件：
 * 1. key 字段用于兼容历史链路；
 * 2. refId 字段用于新协议统一提取；
 * 3. file/txt 场景可通过 accessUrl 暴露下载地址。
 */
public class RawRef {

    /**
     * 存储类型。
     *
     * <p>可选值：mem、redis、file、txt。
     */
    private String store;

    /**
     * 存储键。
     *
     * <p>兼容历史链路使用，建议新链路优先使用 refId。
     */
    private String key;

    /**
     * 统一引用标识。
     *
     * <p>推荐格式：rawref:v1:{store}:{id}。
     */
    private String refId;

    /**
     * 对外访问地址。
     *
     * <p>通常用于 file/txt 存储下载链接。
     */
    private String accessUrl;

    /**
     * 存储原因。
     *
     * <p>示例：default_redis、size_le_1mb_mem、size_gt_1mb_file。
     */
    private String storeReason;

    /**
     * 数据大小（字节）。
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
     * 内容校验哈希。
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

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl) {
        this.accessUrl = accessUrl;
    }

    public String getStoreReason() {
        return storeReason;
    }

    public void setStoreReason(String storeReason) {
        this.storeReason = storeReason;
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

