package com.example.agent.reflection.model;

import java.util.List;

/**
 * 反思输出指纹对象。
 *
 * <p>用途：描述摘要指纹信息，支持模型侧理解输出规模和结构特征。</p>
 */
public class ReflectionOutputDigest {

    /**
     * 输出键数量。
     */
    private final Integer keyCount;

    /**
     * 输出键列表。
     */
    private final List<String> keys;

    /**
     * 输出字符数。
     */
    private final Integer charCount;

    /**
     * 是否截断。
     */
    private final Boolean truncated;

    public ReflectionOutputDigest(Integer keyCount, List<String> keys, Integer charCount, Boolean truncated) {
        this.keyCount = keyCount;
        this.keys = keys;
        this.charCount = charCount;
        this.truncated = truncated;
    }

    public Integer getKeyCount() {
        return keyCount;
    }

    public List<String> getKeys() {
        return keys;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public Boolean getTruncated() {
        return truncated;
    }
}

