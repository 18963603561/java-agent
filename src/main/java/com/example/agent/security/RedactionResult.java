package com.example.agent.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脱敏处理结果，包含拒写与规则命中信息。
 */
public class RedactionResult {

    /**
     * 是否拒写。
     */
    private boolean rejected;

    /**
     * 脱敏替换次数。
     */
    private int redactedCount;

    /**
     * 命中规则统计。
     */
    private final Map<String, Integer> ruleHits = new LinkedHashMap<>();

    /**
     * 处理后的文本。
     */
    private String redactedText;

    public boolean isRejected() {
        return rejected;
    }

    public void setRejected(boolean rejected) {
        this.rejected = rejected;
    }

    public int getRedactedCount() {
        return redactedCount;
    }

    public void setRedactedCount(int redactedCount) {
        this.redactedCount = redactedCount;
    }

    public Map<String, Integer> getRuleHits() {
        return Collections.unmodifiableMap(ruleHits);
    }

    public void addRuleHit(String ruleType, int count) {
        if (ruleType == null || ruleType.isBlank() || count <= 0) {
            return;
        }
        ruleHits.merge(ruleType, count, Integer::sum);
    }

    public String getRedactedText() {
        return redactedText;
    }

    public void setRedactedText(String redactedText) {
        this.redactedText = redactedText;
    }
}
