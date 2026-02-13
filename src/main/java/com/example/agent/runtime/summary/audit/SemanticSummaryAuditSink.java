package com.example.agent.runtime.summary.audit;

/**
 * 语义摘要抽检落库接口。
 */
public interface SemanticSummaryAuditSink {

    /**
     * 记录抽检结果。
     *
     * @param record 抽检记录
     */
    void record(SemanticSummaryAuditRecord record);
}
