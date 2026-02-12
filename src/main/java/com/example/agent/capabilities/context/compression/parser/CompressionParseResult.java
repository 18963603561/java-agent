package com.example.agent.capabilities.context.compression.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 压缩响应解析结果。
 */
public class CompressionParseResult {

    /**
     * 是否成功。
     */
    private boolean success;

    /**
     * 摘要内容。
     */
    private String summary;

    /**
     * 摘要版本。
     */
    private String summaryVersion;

    /**
     * 失败原因。
     */
    private String failureReason;

    /**
     * 违规项。
     */
    private final List<String> violations = new ArrayList<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<String> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public void addViolation(String violation) {
        if (violation == null || violation.isBlank()) {
            return;
        }
        violations.add(violation);
    }
}

