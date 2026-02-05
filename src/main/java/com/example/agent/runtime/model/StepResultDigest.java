package com.example.agent.runtime.model;

import java.util.List;

/**
 * 步骤摘要指纹信息。
 */
public class StepResultDigest {

    private Integer keyCount;
    private List<String> keys;
    private Integer charCount;
    private Boolean truncated;

    public Integer getKeyCount() {
        return keyCount;
    }

    public void setKeyCount(Integer keyCount) {
        this.keyCount = keyCount;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }
}
