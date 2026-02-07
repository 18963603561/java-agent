package com.example.agent.runtime.raw;

/**
 * 原始结果统一提取返回对象。
 */
public class RawResultResolveResult {

    /**
     * 原始传入引用。
     */
    private String inputRef;

    /**
     * 标准化后的统一引用。
     */
    private String resolvedRefId;

    /**
     * 存储类型。
     */
    private String storeType;

    /**
     * 存储原因。
     */
    private String storeReason;

    /**
     * 访问地址。
     */
    private String accessUrl;

    /**
     * 媒体类型。
     */
    private String mediaType;

    /**
     * 内容大小（字节）。
     */
    private Long size;

    /**
     * 原始文本内容。
     */
    private String payload;

    public String getInputRef() {
        return inputRef;
    }

    public void setInputRef(String inputRef) {
        this.inputRef = inputRef;
    }

    public String getResolvedRefId() {
        return resolvedRefId;
    }

    public void setResolvedRefId(String resolvedRefId) {
        this.resolvedRefId = resolvedRefId;
    }

    public String getStoreType() {
        return storeType;
    }

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    public String getStoreReason() {
        return storeReason;
    }

    public void setStoreReason(String storeReason) {
        this.storeReason = storeReason;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl) {
        this.accessUrl = accessUrl;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

