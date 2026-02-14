package com.example.agent.api.http.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultPayloadCompactorTest {

    private ResultPayloadCompactor compactor;

    @BeforeEach
    void setUp() {
        compactor = new ResultPayloadCompactor(new FinalOutputDedupPolicy(), new StepSummaryCompactionPolicy());
    }

    @Test
    void compactShouldRemoveNullAndEmptyNodesRecursively() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("nullValue", null);
        nested.put("emptyList", new ArrayList<>());
        nested.put("emptyMap", new LinkedHashMap<>());
        nested.put("kept", "value");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", "plan-1");
        payload.put("nullable", null);
        payload.put("emptyList", new ArrayList<>());
        payload.put("emptyMap", new LinkedHashMap<>());
        payload.put("nested", nested);

        Map<String, Object> compacted = compactor.compact(payload);

        assertThat(compacted).containsEntry("planId", "plan-1");
        assertThat(compacted).doesNotContainKeys("nullable", "emptyList", "emptyMap");
        assertThat(compacted).containsKey("nested");
        @SuppressWarnings("unchecked")
        Map<String, Object> compactedNested = (Map<String, Object>) compacted.get("nested");
        assertThat(compactedNested).containsEntry("kept", "value");
        assertThat(compactedNested).doesNotContainKeys("nullValue", "emptyList", "emptyMap");
    }

    @Test
    void compactShouldDeduplicatePlanSummaryAndAnswerText() {
        Map<String, Object> payload = buildPayload("同一摘要", "同一摘要", List.of());

        Map<String, Object> compacted = compactor.compact(payload);

        assertThat(compacted).doesNotContainKey("planSummary");
        @SuppressWarnings("unchecked")
        Map<String, Object> finalOutput = (Map<String, Object>) compacted.get("finalOutput");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) finalOutput.get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) finalOutput.get("result");
        assertThat(meta).containsEntry("planSummary", "同一摘要");
        assertThat(result).containsEntry("answer", "同一摘要");
        assertThat(finalOutput).doesNotContainKey("summary");
    }

    @Test
    void compactShouldKeepSummaryWhenStillContainsStructuredInfo() {
        Map<String, Object> payload = buildPayload("同一答案", "同一答案", List.of("命中关键证据"));

        Map<String, Object> compacted = compactor.compact(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> finalOutput = (Map<String, Object>) compacted.get("finalOutput");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) finalOutput.get("summary");
        assertThat(summary).isNotNull();
        assertThat(summary).doesNotContainKey("text");
        assertThat(summary).containsEntry("highlights", List.of("命中关键证据"));
    }

    @Test
    void compactShouldKeepOnlyHighlightsWhenStepSummaryTextMissing() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> stepSummary = new LinkedHashMap<>();
        stepSummary.put("text", null);
        stepSummary.put("highlights", List.of("hit-1", "hit-2"));
        stepSummary.put("risks", List.of("risk-1"));

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("summary", stepSummary);
        payload.put("steps", List.of(step));

        Map<String, Object> compacted = compactor.compact(payload);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) compacted.get("steps");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) steps.get(0).get("summary");
        assertThat(summary).containsEntry("highlights", List.of("hit-1", "hit-2"));
        assertThat(summary).hasSize(1);
    }

    @Test
    void compactShouldRemoveStepSummaryWhenTextAndHighlightsMissing() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> stepSummary = new LinkedHashMap<>();
        stepSummary.put("text", null);
        stepSummary.put("highlights", List.of());
        stepSummary.put("truncated", false);

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("summary", stepSummary);
        step.put("meta", Map.of("seq", 1));
        payload.put("steps", List.of(step));

        Map<String, Object> compacted = compactor.compact(payload);

        assertThat(compacted).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) compacted.get("steps");
        assertThat(steps.get(0)).doesNotContainKey("summary");
    }

    private Map<String, Object> buildPayload(String answer, String summaryText, List<String> highlights) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("planSummary", "同一摘要");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("text", summaryText);
        summary.put("highlights", highlights);
        summary.put("openQuestions", new ArrayList<>());

        Map<String, Object> finalOutput = new LinkedHashMap<>();
        finalOutput.put("meta", meta);
        finalOutput.put("result", result);
        finalOutput.put("summary", summary);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planSummary", "同一摘要");
        payload.put("finalOutput", finalOutput);
        return payload;
    }
}
