package com.example.agent.streaming.payload;

import com.example.agent.budget.trim.ContextTrimStats;
import java.util.List;
import java.util.Map;

/**
 * 上下文裁剪摘要，用于事件载荷中的裁剪统计。
 */
public class ContextTrimSummary {

    /**
     * 报告版本号，可为空。
     */
    private String version;

    /**
     * 裁剪前总 token 估算。
     */
    private Integer beforeTokens;

    /**
     * 裁剪后总 token 估算。
     */
    private Integer afterTokens;

    /**
     * 各分区移除统计，key 为分区名称。
     */
    private Map<String, ContextTrimStats> removedBySection;

    /**
     * 裁剪原因列表。
     */
    private List<String> reasons;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getBeforeTokens() {
        return beforeTokens;
    }

    public void setBeforeTokens(Integer beforeTokens) {
        this.beforeTokens = beforeTokens;
    }

    public Integer getAfterTokens() {
        return afterTokens;
    }

    public void setAfterTokens(Integer afterTokens) {
        this.afterTokens = afterTokens;
    }

    public Map<String, ContextTrimStats> getRemovedBySection() {
        return removedBySection;
    }

    public void setRemovedBySection(Map<String, ContextTrimStats> removedBySection) {
        this.removedBySection = removedBySection;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
