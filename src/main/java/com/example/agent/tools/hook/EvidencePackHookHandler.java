package com.example.agent.tools.hook;

import com.example.agent.context.EvidenceItem;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.EvidencePackService;
import com.example.agent.research.ResearchCitation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 证据包 Hook 处理器，统一在 Hook 生命周期中写入证据。
 */
@Component
public class EvidencePackHookHandler implements HookHandler {

    private static final Logger log = LoggerFactory.getLogger(EvidencePackHookHandler.class);

    private final EvidencePackService evidencePackService;

    public EvidencePackHookHandler(EvidencePackService evidencePackService) {
        this.evidencePackService = evidencePackService;
    }

    @Override
    public String getHookId() {
        return "evidence-pack";
    }

    @Override
    public HookDecision handle(HookContext context) {
        if (context == null || context.getHookType() == null) {
            return allow("context_missing");
        }
        Map<String, Object> payload = context.getPayload();
        if (payload == null) {
            return allow("payload_missing");
        }
        String tenantId = context.getTenantId();
        String workflowId = readString(payload, "workflowId");
        if (!StringUtils.hasText(workflowId)) {
            return allow("workflow_missing");
        }
        String snapshotId = readString(payload, "snapshotId");
        String stepId = firstNonBlank(context.getStepId(), readString(payload, "stepId"));
        EvidencePack pack = evidencePackService.getOrCreatePack(tenantId, workflowId, snapshotId);
        switch (context.getHookType()) {
            case POST_TOOL -> appendToolEvidence(pack, stepId, context, payload, tenantId, workflowId);
            case POST_RECALL -> appendRecallEvidence(pack, stepId, payload, tenantId, workflowId);
            case POST_RESEARCH -> appendResearchEvidence(pack, stepId, payload, tenantId, workflowId);
            case POST_TRIM -> appendTrimEvidence(pack, stepId, payload, tenantId, workflowId);
            default -> {
                return allow("skip_" + context.getHookType().name().toLowerCase());
            }
        }
        evidencePackService.finalizePack(pack, tenantId, workflowId);
        return allow("ok");
    }

    private void appendToolEvidence(EvidencePack pack,
                                    String stepId,
                                    HookContext context,
                                    Map<String, Object> payload,
                                    String tenantId,
                                    String workflowId) {
        String ref = resolveRef(payload);
        String digest = resolveDigest(payload);
        String source = firstNonBlank(context.getToolName(), readString(payload, "toolName"), "unknown_tool");
        evidencePackService.appendToolResult(pack, stepId, source, ref, digest, tenantId, workflowId);
    }

    private void appendRecallEvidence(EvidencePack pack,
                                      String stepId,
                                      Map<String, Object> payload,
                                      String tenantId,
                                      String workflowId) {
        Object recordsObj = payload.get("records");
        if (recordsObj instanceof List<?> records && !records.isEmpty()) {
            for (Object record : records) {
                String memoryId = resolveMemoryId(record);
                if (!StringUtils.hasText(memoryId)) {
                    continue;
                }
                evidencePackService.appendMemory(pack,
                        stepId,
                        "memory",
                        memoryId,
                        "memoryId=" + memoryId,
                        tenantId,
                        workflowId);
            }
            return;
        }
        Integer count = resolveInt(payload.get("count"));
        if (count != null && count > 0) {
            evidencePackService.appendMemory(pack,
                    stepId,
                    "memory",
                    "memory:count",
                    "count=" + count,
                    tenantId,
                    workflowId);
        }
    }

    private void appendResearchEvidence(EvidencePack pack,
                                        String stepId,
                                        Map<String, Object> payload,
                                        String tenantId,
                                        String workflowId) {
        Object citationsObj = payload.get("citations");
        if (!(citationsObj instanceof List<?> citations) || citations.isEmpty()) {
            return;
        }
        List<ResearchCitation> normalized = new ArrayList<>();
        for (Object item : citations) {
            if (item instanceof ResearchCitation citation) {
                normalized.add(citation);
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                ResearchCitation citation = new ResearchCitation();
                citation.setSource(readString(map, "source"));
                citation.setSnippet(readString(map, "snippet"));
                normalized.add(citation);
            }
        }
        evidencePackService.appendResearchCitations(pack, stepId, normalized, tenantId, workflowId);
    }

    private void appendTrimEvidence(EvidencePack pack,
                                    String stepId,
                                    Map<String, Object> payload,
                                    String tenantId,
                                    String workflowId) {
        String ref = readString(payload, "reportRef");
        String digest = firstNonBlank(readString(payload, "summary"), "trimmed");
        evidencePackService.appendTruncation(pack, stepId, "context_trim", ref, digest, tenantId, workflowId);
    }

    private String resolveRef(Map<String, Object> payload) {
        String direct = readString(payload, "rawRef");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        Object result = payload.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            String nested = readString(resultMap, "rawRef");
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        return null;
    }

    private String resolveDigest(Map<String, Object> payload) {
        String digest = readString(payload, "resultDigest");
        if (StringUtils.hasText(digest)) {
            return digest;
        }
        Object result = payload.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            return "resultKeys=" + resultMap.keySet();
        }
        return "result=unknown";
    }

    private String resolveMemoryId(Object value) {
        if (value instanceof Map<?, ?> map) {
            return readString(map, "memoryId");
        }
        try {
            Object methodValue = value != null
                    ? value.getClass().getMethod("getMemoryId").invoke(value)
                    : null;
            return methodValue instanceof String text ? text : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readString(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private Integer resolveInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private HookDecision allow(String reason) {
        return new HookDecision(true, reason, Map.of());
    }
}
