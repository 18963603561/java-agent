package com.example.agent.runtime.step;

import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 步骤事件载荷工厂。
 *
 * <p>用途：统一构造步骤开始、完成、失败事件的标准载荷，避免散落硬编码。
 * <p>输入：步骤记录与失败上下文。
 * <p>输出：不可变事件载荷映射。
 * <p>边界：仅负责数据拼装，不执行事件发布。
 */
@Component
public class StepEventPayloadFactory {

    /**
     * 构建步骤开始事件载荷。
     *
     * @param record 步骤记录
     * @return 事件载荷
     */
    public Map<String, Object> buildStepStartedPayload(StepRecord record) {
        return Map.of(
                "stepId", record.getStepId(),
                "stepSeq", record.getStepSeq(),
                "status", record.getStatus().name(),
                "type", record.getType(),
                "attempt", record.getAttempt()
        );
    }

    /**
     * 构建步骤完成事件载荷。
     *
     * @param record 步骤记录
     * @return 事件载荷
     */
    public Map<String, Object> buildStepCompletedPayload(StepRecord record) {
        return Map.of(
                "stepId", record.getStepId(),
                "stepSeq", record.getStepSeq(),
                "status", record.getStatus().name()
        );
    }

    /**
     * 构建步骤失败事件载荷。
     *
     * @param record 步骤记录
     * @param errorCode 错误码
     * @param details 错误详情
     * @return 事件载荷
     */
    public Map<String, Object> buildStepFailedPayload(StepRecord record,
                                                      String errorCode,
                                                      Map<String, Object> details) {
        return Map.of(
                "stepId", record.getStepId(),
                "stepSeq", record.getStepSeq(),
                "status", record.getStatus().name(),
                "errorCode", errorCode,
                "details", details == null ? Collections.emptyMap() : details
        );
    }
}

