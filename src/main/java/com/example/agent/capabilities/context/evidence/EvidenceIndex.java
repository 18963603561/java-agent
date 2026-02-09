package com.example.agent.capabilities.context.evidence;

import java.util.List;
import java.util.Map;

/**
 * 证据索引结构，用于按步骤和类型快速定位证据项。
 */
public class EvidenceIndex {

    /**
     * 按步骤标识聚合的证据标识列表。
     */
    private Map<String, List<String>> byStepId;

    /**
     * 按证据类型聚合的证据标识列表。
     */
    private Map<String, List<String>> byType;

    public Map<String, List<String>> getByStepId() {
        return byStepId;
    }

    public void setByStepId(Map<String, List<String>> byStepId) {
        this.byStepId = byStepId;
    }

    public Map<String, List<String>> getByType() {
        return byType;
    }

    public void setByType(Map<String, List<String>> byType) {
        this.byType = byType;
    }
}
