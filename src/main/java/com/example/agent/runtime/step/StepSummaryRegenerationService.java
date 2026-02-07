package com.example.agent.runtime.step;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.summary.StepSummaryBuildInput;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 步骤摘要重建服务。
 *
 * <p>用途：统一判断是否需要重建摘要，并在需要时按契约重建摘要与输出日志。
 * <p>输入：步骤记录、步骤输出与当前摘要映射。
 * <p>输出：最终摘要映射（可能为原摘要或重建摘要）。
 * <p>边界：该服务不修改步骤状态，仅处理摘要相关逻辑。
 */
@Component
public class StepSummaryRegenerationService {

    private static final Logger log = LoggerFactory.getLogger(StepSummaryRegenerationService.class);

    /**
     * 摘要构建器。
     */
    private final StepOutputSummaryBuilder stepOutputSummaryBuilder;

    public StepSummaryRegenerationService(StepOutputSummaryBuilder stepOutputSummaryBuilder) {
        this.stepOutputSummaryBuilder = stepOutputSummaryBuilder;
    }

    /**
     * 获取最终摘要。
     *
     * @param record 步骤记录
     * @param output 步骤输出
     * @param rawOutput 原始输出映射
     * @param currentSummary 当前摘要
     * @return 最终摘要映射
     */
    public Map<String, Object> resolveSummary(StepRecord record,
                                              StepExecutionOutput output,
                                              Map<String, Object> rawOutput,
                                              Map<String, Object> currentSummary) {
        boolean summaryEnabled = stepOutputSummaryBuilder != null && stepOutputSummaryBuilder.isEnabled();
        if (!summaryEnabled) {
            return currentSummary;
        }
        if (!shouldRegenerate(currentSummary, record.getStatus())) {
            return currentSummary;
        }
        if (currentSummary != null && !currentSummary.isEmpty()) {
            log.debug("步骤摘要状态不一致, 重新生成摘要, tenantId={}, workflowId={}, stepId={}, expectedStatus={}",
                    record.getTenantId(),
                    record.getWorkflowId(),
                    record.getStepId(),
                    record.getStatus() != null ? record.getStatus().name() : null);
        }
        long summaryStart = System.nanoTime();
        Map<String, Object> regenerated = stepOutputSummaryBuilder.build(StepSummaryBuildInput.builder()
                .stepId(record.getStepId())
                .stepType(record.getType())
                .status(record.getStatus() != null ? record.getStatus().name() : null)
                .attempt(record.getAttempt())
                .stepInput(record.getInput())
                .inputSource("record")
                .output(rawOutput)
                .toolName(output != null ? output.getToolName() : null)
                .build());
        long summaryMs = (System.nanoTime() - summaryStart) / 1_000_000;
        logSummary(record, regenerated, summaryMs);
        return regenerated;
    }

    private boolean shouldRegenerate(Map<String, Object> summary, StepState expected) {
        if (summary == null || summary.isEmpty()) {
            return true;
        }
        if (expected == null) {
            return false;
        }
        return isSummaryStatusMismatch(summary, expected);
    }

    private boolean isSummaryStatusMismatch(Map<String, Object> summary, StepState expected) {
        if (summary == null || summary.isEmpty() || expected == null) {
            return false;
        }
        String expectedText = expected.name();
        Object stepSummaryObj = summary.get(RuntimeOutputKeys.STEP_SUMMARY);
        if (stepSummaryObj instanceof Map<?, ?> stepSummary) {
            Object status = stepSummary.get("status");
            if (status != null) {
                return !expectedText.equalsIgnoreCase(String.valueOf(status));
            }
        }
        Object outputSummaryObj = summary.get(RuntimeOutputKeys.OUTPUT_SUMMARY);
        if (outputSummaryObj instanceof Map<?, ?> outputSummary) {
            Object status = outputSummary.get("status");
            if (status != null) {
                return !expectedText.equalsIgnoreCase(String.valueOf(status));
            }
        }
        return false;
    }

    private void logSummary(StepRecord record, Map<String, Object> summary, long summaryMs) {
        if (summary == null || summary.isEmpty()) {
            return;
        }
        Map<String, Object> digest = summary.get(RuntimeOutputKeys.OUTPUT_DIGEST) instanceof Map<?, ?> map
                ? new HashMap<>(map.size())
                : null;
        if (summary.get(RuntimeOutputKeys.OUTPUT_DIGEST) instanceof Map<?, ?> map) {
            map.forEach((key, value) -> digest.put(String.valueOf(key), value));
        }
        Integer charCount = digest != null ? resolveInt(digest.get(RuntimeOutputKeys.CHAR_COUNT)) : null;
        Integer keyCount = digest != null ? resolveInt(digest.get(RuntimeOutputKeys.KEY_COUNT)) : null;
        Boolean truncated = summary.get(RuntimeOutputKeys.TRUNCATED) instanceof Boolean value ? value : null;
        String toolName = null;
        if (summary.get(RuntimeOutputKeys.STEP_SUMMARY) instanceof Map<?, ?> stepSummary) {
            Object tool = stepSummary.get(RuntimeOutputKeys.TOOL_NAME);
            if (tool != null) {
                toolName = String.valueOf(tool);
            }
        }
        log.info("步骤摘要生成完成, tenantId={}, workflowId={}, stepType={}, toolName={}, truncated={}, charCount={}, keyCount={}, durationMs={}",
                record.getTenantId(),
                record.getWorkflowId(),
                record.getType(),
                toolName,
                truncated,
                charCount,
                keyCount,
                summaryMs);
    }

    private Integer resolveInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}

