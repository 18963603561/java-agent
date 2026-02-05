package com.example.agent.budget.trim;

import java.util.List;
import java.util.Map;

/**
 * 上下文裁剪报告，用于记录裁剪前后与原因。
 */
public class ContextTrimReport {

    /**
     * 报告版本号，默认 v1。
     */
    private String version = "v1";

    /**
     * 裁剪前总令牌估算。
     */
    private Integer totalBeforeTokens;

    /**
     * 裁剪后总令牌估算。
     */
    private Integer totalAfterTokens;

    /**
     * 各分区裁剪前令牌估算。
     */
    private Map<ContextSection, Integer> sectionTokensBefore;

    /**
     * 各分区裁剪后令牌估算。
     */
    private Map<ContextSection, Integer> sectionTokensAfter;

    /**
     * 各分区移除统计。
     */
    private Map<ContextSection, ContextTrimStats> removedItemsBySection;

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

    public Integer getTotalBeforeTokens() {
        return totalBeforeTokens;
    }

    public void setTotalBeforeTokens(Integer totalBeforeTokens) {
        this.totalBeforeTokens = totalBeforeTokens;
    }

    public Integer getTotalAfterTokens() {
        return totalAfterTokens;
    }

    public void setTotalAfterTokens(Integer totalAfterTokens) {
        this.totalAfterTokens = totalAfterTokens;
    }

    public Map<ContextSection, Integer> getSectionTokensBefore() {
        return sectionTokensBefore;
    }

    public void setSectionTokensBefore(Map<ContextSection, Integer> sectionTokensBefore) {
        this.sectionTokensBefore = sectionTokensBefore;
    }

    public Map<ContextSection, Integer> getSectionTokensAfter() {
        return sectionTokensAfter;
    }

    public void setSectionTokensAfter(Map<ContextSection, Integer> sectionTokensAfter) {
        this.sectionTokensAfter = sectionTokensAfter;
    }

    public Map<ContextSection, ContextTrimStats> getRemovedItemsBySection() {
        return removedItemsBySection;
    }

    public void setRemovedItemsBySection(Map<ContextSection, ContextTrimStats> removedItemsBySection) {
        this.removedItemsBySection = removedItemsBySection;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
