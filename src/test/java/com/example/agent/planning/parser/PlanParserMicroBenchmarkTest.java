package com.example.agent.planning.parser;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.api.http.dto.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规划解析微基准测试。
 *
 * <p>用途：提供可重复执行的轻量级解析性能基线，不接入运行时主链路。
 */
class PlanParserMicroBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(PlanParserMicroBenchmarkTest.class);

    @Test
    void parseAttemptShouldMeetBaselineUnderBatchLoad() {
        PlanParser parser = new PlanParser(new ObjectMapper());
        TaskRequest request = new TaskRequest();
        request.setQuery("benchmark-query");
        Map<String, Object> context = Map.of("tenantId", "benchmark-tenant", "mode", "react");

        String json = buildBatchJson(60);

        int warmupRounds = 200;
        int measureRounds = 2000;
        runBatch(parser, request, context, json, warmupRounds);

        long start = System.nanoTime();
        runBatch(parser, request, context, json, measureRounds);
        long elapsedNs = System.nanoTime() - start;

        double avgMs = (elapsedNs / 1_000_000.0D) / measureRounds;
        log.info("PlanParser 微基准完成, stepCount={}, rounds={}, avgMs={}", 60, measureRounds, avgMs);
        assertTrue(avgMs > 0.0D);
        assertTrue(avgMs < 100.0D);
    }

    private void runBatch(PlanParser parser,
                          TaskRequest request,
                          Map<String, Object> context,
                          String json,
                          int rounds) {
        for (int i = 0; i < rounds; i++) {
            PlanParseAttemptResult result = parser.parseAttempt(json, request, context);
            assertNotNull(result);
        }
    }

    private String buildBatchJson(int stepCount) {
        List<Map<String, Object>> steps = new java.util.ArrayList<>(stepCount);
        for (int index = 0; index < stepCount; index++) {
            Map<String, Object> step = new HashMap<>();
            step.put("type", "TOOL");
            step.put("tool", "demo_tool_" + index);
            step.put("input", Map.of(
                    "arguments", Map.of("query", "q-" + index, "limit", 5),
                    "dependsOn", List.of(index == 0 ? "start" : "step-" + (index - 1))
            ));
            steps.add(step);
        }
        Map<String, Object> root = Map.of(
                "summary", "benchmark",
                "steps", steps
        );
        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("benchmark_json_build_failed", exception);
        }
    }
}
