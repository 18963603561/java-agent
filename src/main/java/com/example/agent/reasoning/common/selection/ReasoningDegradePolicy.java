package com.example.agent.reasoning.common.selection;

import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 推理降级策略。
 *
 * <p>用途：定义主策略失败后的降级链路，保证推理执行可恢复。
 */
@Component
public class ReasoningDegradePolicy {

    private final ReasoningConfigResolver reasoningConfigResolver;

    public ReasoningDegradePolicy(ReasoningConfigResolver reasoningConfigResolver) {
        this.reasoningConfigResolver = reasoningConfigResolver;
    }

    /**
     * 解析降级顺序。
     *
     * @param primaryStrategy 主策略
     * @return 降级链路（包含主策略）
     */
    public List<String> resolveFallbackOrder(String primaryStrategy) {
        return reasoningConfigResolver.resolveFallbackOrder(primaryStrategy);
    }
}
