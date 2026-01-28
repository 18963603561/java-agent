package com.example.agent.budget;

import com.example.agent.context.ContextSnapshot;

/**
 * 上下文裁剪结果。
 */
public class ContextTrimResult {

    /**
     * 裁剪后的快照。
     */
    private ContextSnapshot trimmedSnapshot;

    /**
     * 裁剪报告。
     */
    private ContextTrimReport report;

    public ContextSnapshot getTrimmedSnapshot() {
        return trimmedSnapshot;
    }

    public void setTrimmedSnapshot(ContextSnapshot trimmedSnapshot) {
        this.trimmedSnapshot = trimmedSnapshot;
    }

    public ContextTrimReport getReport() {
        return report;
    }

    public void setReport(ContextTrimReport report) {
        this.report = report;
    }
}
