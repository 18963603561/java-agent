package com.example.agent.budget.trim.estimator;

import com.example.agent.capabilities.context.evidence.EvidenceItem;
import com.example.agent.capabilities.context.evidence.EvidencePack;

/**
 * 证据与知识相关令牌估算器。
 */
class ContextKnowledgeTokenEstimator {

    private final ContextTextTokenEstimator textEstimator;

    ContextKnowledgeTokenEstimator(ContextTextTokenEstimator textEstimator) {
        this.textEstimator = textEstimator;
    }

    /**
     * 估算证据包令牌。
     */
    int estimateEvidencePackTokens(EvidencePack pack) {
        if (pack == null) {
            return 0;
        }
        int total = 0;
        if (pack.getEvidences() != null) {
            for (EvidenceItem item : pack.getEvidences()) {
                total += estimateEvidenceItemTokens(item);
            }
        }
        return total;
    }

    /**
     * 估算证据项令牌。
     */
    int estimateEvidenceItemTokens(EvidenceItem item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(item.getType() != null ? item.getType().name() : null);
        total += textEstimator.estimateTokens(item.getEvidenceId());
        total += textEstimator.estimateTokens(item.getStepId());
        total += textEstimator.estimateTokens(item.getSource());
        total += textEstimator.estimateTokens(item.getRef());
        total += textEstimator.estimateTokens(item.getDigest());
        return total;
    }

    /**
     * 估算证据项字符数。
     */
    int estimateEvidenceItemChars(EvidenceItem item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.safeLength(item.getType() != null ? item.getType().name() : null);
        total += textEstimator.safeLength(item.getEvidenceId());
        total += textEstimator.safeLength(item.getStepId());
        total += textEstimator.safeLength(item.getSource());
        total += textEstimator.safeLength(item.getRef());
        total += textEstimator.safeLength(item.getDigest());
        return total;
    }
}
