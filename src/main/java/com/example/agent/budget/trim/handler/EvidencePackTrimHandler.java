package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.capabilities.context.evidence.EvidenceItem;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.evidence.EvidenceType;
import com.example.agent.capabilities.context.model.WorkingMemory;
import java.util.List;

/**
 * 证据包分组裁剪处理器。
 */
public class EvidencePackTrimHandler extends AbstractContextTrimHandler {

    @Override
    public ContextTrimSection section() {
        return ContextTrimSection.EVIDENCE_PACK;
    }

    @Override
    public void trimBySectionBudget(TrimContext context) {
        Integer budget = resolveBudget(context.getBudgets(), ContextSection.EVIDENCE_PACK);
        int current = context.getCurrentTokens().getOrDefault(ContextSection.EVIDENCE_PACK, 0);
        if (budget != null && current > budget) {
            trimEvidencePack(context, budget);
            context.refreshTokens();
        }
    }

    @Override
    public int trimByTotalBudget(TrimContext context, int excess) {
        if (excess <= 0) {
            return 0;
        }
        int current = context.getCurrentTokens().getOrDefault(ContextSection.EVIDENCE_PACK, 0);
        if (current <= 0) {
            return excess;
        }
        int target = Math.max(0, current - excess);
        int after = trimEvidencePack(context, target);
        context.refreshTokens();
        return Math.max(0, excess - Math.max(0, current - after));
    }

    private int trimEvidencePack(TrimContext context, int targetTokens) {
        WorkingMemory memory = context.getSnapshot().getWorkingMemory();
        if (memory == null || memory.getEvidencePack() == null) {
            return 0;
        }
        EvidencePack pack = memory.getEvidencePack();
        ContextTokenEstimator estimator = context.getEstimator();
        int currentTokens = estimator.estimateEvidencePackTokens(pack);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<EvidenceItem> evidences = mutableCopy(pack.getEvidences());
        if (evidences == null || evidences.isEmpty()) {
            return currentTokens;
        }
        for (EvidenceItem item : evidences) {
            if (item == null) {
                continue;
            }
            int maxDigestChars = item.getType() == EvidenceType.TOOL_RESULT
                    ? MAX_RESULT_DIGEST_CHARS
                    : MAX_ARGS_DIGEST_CHARS;
            String digest = item.getDigest();
            String trimmedDigest = trimText(digest, maxDigestChars);
            if (digest != null && trimmedDigest != null && digest.length() > trimmedDigest.length()) {
                int removedChars = digest.length() - trimmedDigest.length();
                recordRemoved(context, ContextSection.EVIDENCE_PACK, 1,
                        removedChars, estimator.estimateTokensByChars(removedChars));
                item.setDigest(trimmedDigest);
            }
            String source = item.getSource();
            String trimmedSource = trimText(source, MAX_TOOL_DESC_CHARS);
            if (source != null && trimmedSource != null && source.length() > trimmedSource.length()) {
                int removedChars = source.length() - trimmedSource.length();
                recordRemoved(context, ContextSection.EVIDENCE_PACK, 1,
                        removedChars, estimator.estimateTokensByChars(removedChars));
                item.setSource(trimmedSource);
            }
        }
        pack.setEvidences(evidences);
        currentTokens = estimator.estimateEvidencePackTokens(pack);
        if (currentTokens <= targetTokens) {
            pack.recomputeStats();
            return currentTokens;
        }
        while (!evidences.isEmpty() && currentTokens > targetTokens) {
            EvidenceItem removed = evidences.remove(0);
            int removedChars = estimator.estimateEvidenceItemChars(removed);
            int removedTokens = estimator.estimateEvidenceItemTokens(removed);
            recordRemoved(context, ContextSection.EVIDENCE_PACK, 1, removedChars, removedTokens);
            pack.setEvidences(evidences.isEmpty() ? null : evidences);
            currentTokens = estimator.estimateEvidencePackTokens(pack);
        }
        pack.setEvidences(evidences.isEmpty() ? null : evidences);
        pack.recomputeStats();
        return estimator.estimateEvidencePackTokens(pack);
    }
}
