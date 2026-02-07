package com.example.agent.runtime.raw;

/**
 * 原始数据存储类型枚举。
 */
public enum RawStoreType {

    /**
     * 内存存储。
     */
    MEM("mem"),

    /**
     * Redis 存储。
     */
    REDIS("redis"),

    /**
     * 文件存储。
     */
    FILE("file"),

    /**
     * 文本文件存储别名。
     */
    TXT("txt");

    private final String code;

    RawStoreType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据编码解析存储类型。
     *
     * @param code 编码
     * @return 对应枚举
     */
    public static RawStoreType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return MEM;
        }
        String normalized = code.trim().toLowerCase();
        for (RawStoreType value : values()) {
            if (value.code.equals(normalized)) {
                return value;
            }
        }
        if ("memory".equals(normalized)) {
            return MEM;
        }
        throw new IllegalArgumentException("未知存储类型: " + code);
    }
}

