package com.example.agent.runtime.summary.audit;

import org.springframework.stereotype.Component;

/**
 * 语义摘要抽检空实现。
 */
@Component
public class NoopSemanticSummaryAuditSink implements SemanticSummaryAuditSink {

    @Override
    public void record(SemanticSummaryAuditRecord record) {
        // 空实现：默认不落库。
    }
}
