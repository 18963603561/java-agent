package com.example.agent.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.summary.StepSummaryProperties;
import com.example.agent.runtime.summary.SummaryDigestService;
import com.example.agent.runtime.summary.SummaryInputSanitizer;
import com.example.agent.runtime.summary.SummarySnapshotService;
import com.example.agent.runtime.summary.StepSummaryTextService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepOutputSummaryBuilderTest {

    private StepRecord record(String stepId) {
        StepRecord record = new StepRecord();
        record.setStepId(stepId);
        record.setType("TOOL");
        record.setStatus(StepState.COMPLETED);
        record.setAttempt(1);
        return record;
    }

    @Test
    void buildUsesBoundedSnapshotWithoutFullSerialization() {
        StepSummaryProperties properties = new StepSummaryProperties();
        properties.setEnable(true);
        properties.setMaxChars(50);
        properties.setMaxListItems(2);
        properties.setMaxFieldChars(10);
        StepOutputSummaryBuilder builder = buildBuilder(properties);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("items", List.of("item-1", "item-2", "item-3", "item-4"));
        output.put("nested", List.of(List.of("a", "b", "c"), List.of("d", "e")));
        output.put("explosive", new ExplosiveBean());

        Map<String, Object> summary = builder.build(record("s-1"), null, output, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> digest = (Map<String, Object>) summary.get("outputDigest");

        int charCount = ((Number) digest.get("charCount")).intValue();
        assertTrue(charCount <= properties.getMaxChars());
        assertTrue(Boolean.TRUE.equals(digest.get("truncated")));
    }

    @Test
    void listTruncatesByMaxListItems() {
        StepSummaryProperties properties = new StepSummaryProperties();
        properties.setEnable(true);
        properties.setMaxChars(1000);
        properties.setMaxListItems(2);
        properties.setMaxFieldChars(1000);
        StepOutputSummaryBuilder builder = buildBuilder(properties);

        List<String> output = List.of("a", "b", "c", "d");
        Map<String, Object> summary = builder.build(record("s-2"), null, output, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputSummary = (Map<String, Object>) summary.get("outputSummary");
        String sample = outputSummary != null ? (String) outputSummary.get("sample") : null;

        assertNotNull(sample);
        assertTrue(sample.contains("a"));
        assertTrue(sample.contains("b"));
        assertFalse(sample.contains("c"));
        @SuppressWarnings("unchecked")
        Map<String, Object> digest = (Map<String, Object>) summary.get("outputDigest");
        assertTrue(Boolean.TRUE.equals(digest.get("truncated")));
    }

    @Test
    void arrayCycleDoesNotOverflow() {
        StepSummaryProperties properties = new StepSummaryProperties();
        properties.setEnable(true);
        properties.setMaxChars(200);
        properties.setMaxListItems(1);
        properties.setMaxFieldChars(50);
        StepOutputSummaryBuilder builder = buildBuilder(properties);

        Object[] array = new Object[2];
        array[0] = array;
        array[1] = "tail";

        Map<String, Object> summary = builder.build(record("s-3"), null, array, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputSummary = (Map<String, Object>) summary.get("outputSummary");
        String sample = outputSummary != null ? (String) outputSummary.get("sample") : null;

        assertNotNull(sample);
        assertTrue(sample.contains("<cycle>"));
        assertFalse(sample.contains("tail"));
        @SuppressWarnings("unchecked")
        Map<String, Object> digest = (Map<String, Object>) summary.get("outputDigest");
        assertTrue(Boolean.TRUE.equals(digest.get("truncated")));
    }

    @Test
    void toStringFailureDoesNotBreakSummary() {
        StepSummaryProperties properties = new StepSummaryProperties();
        properties.setEnable(true);
        properties.setMaxChars(200);
        properties.setMaxListItems(5);
        properties.setMaxFieldChars(100);
        StepOutputSummaryBuilder builder = buildBuilder(properties);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("bad", new BadToString());

        Map<String, Object> summary = builder.build(record("s-4"), null, output, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputSummary = (Map<String, Object>) summary.get("outputSummary");
        String sample = outputSummary != null ? (String) outputSummary.get("sample") : null;

        assertNotNull(sample);
        assertTrue(sample.contains("<toString_error:BadToString>"));
    }

    private static final class ExplosiveBean {
        public String getBoom() {
            throw new IllegalStateException("boom");
        }

        @Override
        public String toString() {
            return "explosive";
        }
    }

    private StepOutputSummaryBuilder buildBuilder(StepSummaryProperties properties) {
        return new StepOutputSummaryBuilder(
                properties,
                new SummarySnapshotService(),
                new SummaryInputSanitizer(),
                new StepSummaryTextService(),
                new SummaryDigestService()
        );
    }

    private static final class BadToString {
        @Override
        public String toString() {
            throw new RuntimeException("bad");
        }
    }
}
