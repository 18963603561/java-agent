package com.example.agent.capabilities.context.compression.application;

import com.example.agent.budget.trim.model.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.application.port.CompressionExecutor;
import com.example.agent.capabilities.context.compression.application.port.CompressionModeResolver;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.domain.policy.CompressionFallbackPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 压缩执行路由器。
 *
 * <p>用途：根据模式解析结果路由到对应执行器，并在模式不可用时执行规则压缩兜底。</p>
 */
@Component
public class CompressionExecutionRouter {

    private static final Logger log = LoggerFactory.getLogger(CompressionExecutionRouter.class);

    private static final String MODE_RULE = "rule";

    /**
     * 模式解析器。
     */
    private final CompressionModeResolver modeResolver;

    /**
     * 执行器映射。
     */
    private final Map<String, CompressionExecutor> executors;

    /**
     * 压缩降级策略。
     */
    private final CompressionFallbackPolicy fallbackPolicy;

    public CompressionExecutionRouter(CompressionModeResolver modeResolver,
                                      List<CompressionExecutor> executors,
                                      CompressionFallbackPolicy fallbackPolicy) {
        this.modeResolver = modeResolver;
        this.executors = toExecutorMap(executors);
        this.fallbackPolicy = fallbackPolicy;
    }

    /**
     * 按模式执行压缩。
     *
     * @param command 压缩命令
     * @return 执行结果
     */
    public CompressionExecutionResult execute(CompressionCommand command) {
        // 模式解析：优先使用模式解析器结果，缺失时回退 rule。
        String mode = modeResolver != null ? modeResolver.resolveMode() : MODE_RULE;
        // 调用重载方法：复用统一执行流程并支持外部显式传入模式。
        return execute(command, mode);
    }

    /**
     * 按指定模式执行压缩。
     *
     * @param command 压缩命令
     * @param mode 指定模式
     * @return 执行结果
     */
    public CompressionExecutionResult execute(CompressionCommand command, String mode) {
        // 模式归一化：空模式时回退 rule，避免映射 miss。
        String resolvedMode = StringUtils.hasText(mode) ? mode.trim().toLowerCase() : MODE_RULE;
        // 主路径：优先按配置模式命中执行器。
        CompressionExecutor targetExecutor = executors.get(resolvedMode);
        if (targetExecutor != null) {
            // 调用执行器：由目标执行器返回统一结果对象。
            CompressionExecutionResult targetResult = targetExecutor.execute(command);
            // 降级判定：执行失败时由策略决定是否回退 rule 执行器。
            if (fallbackPolicy != null && fallbackPolicy.shouldFallback(resolvedMode, targetResult)) {
                CompressionExecutionResult fallbackResult = executeRuleFallback(command, resolvedMode);
                if (fallbackResult == null) {
                    return targetResult;
                }
                fallbackResult.setFallbackApplied(true);
                // 降级标记：保留原失败原因，便于追踪触发降级的根因。
                if (StringUtils.hasText(targetResult.getFailureReason())) {
                    fallbackResult.setFailureReason(targetResult.getFailureReason());
                }
                return fallbackResult;
            }
            return targetResult;
        }

        // 降级路径：配置模式无执行器时告警并回退 rule。
        log.warn("压缩模式未命中执行器，回退规则压缩, mode={}", resolvedMode);
        CompressionExecutionResult modeFallback = executeRuleFallback(command, resolvedMode);
        if (modeFallback != null) {
            modeFallback.setFallbackApplied(true);
            modeFallback.setFailureReason("MODE_NOT_SUPPORTED:" + resolvedMode);
            return modeFallback;
        }

        // 失败路径：当无任何可用执行器时返回失败原因，避免抛异常中断主链路。
        CompressionExecutionResult failed = new CompressionExecutionResult();
        failed.setSuccess(false);
        failed.setSource(resolvedMode);
        failed.setFailureReason("NO_AVAILABLE_EXECUTOR");
        return failed;
    }

    /**
     * 执行规则压缩降级。
     */
    private CompressionExecutionResult executeRuleFallback(CompressionCommand command, String mode) {
        CompressionExecutor fallbackExecutor = executors.get(MODE_RULE);
        if (fallbackExecutor == null) {
            return null;
        }
        // 执行降级：调用 rule 执行器作为统一兜底路径。
        CompressionExecutionResult fallbackResult = fallbackExecutor.execute(command);
        fallbackResult.setSource(MODE_RULE);
        return fallbackResult;
    }

    /**
     * 将执行器列表构建为模式映射。
     */
    private Map<String, CompressionExecutor> toExecutorMap(List<CompressionExecutor> executorList) {
        Map<String, CompressionExecutor> mapping = new HashMap<>();
        if (executorList == null || executorList.isEmpty()) {
            return mapping;
        }
        // 遍历注册执行器：将声明模式归一化后写入映射表。
        for (CompressionExecutor executor : executorList) {
            // 安全判断：忽略空执行器，避免容器装配异常导致 NPE。
            if (executor == null) {
                continue;
            }
            String mode = executor.mode();
            // 过滤非法模式：空模式不注册，避免覆盖有效执行器。
            if (!StringUtils.hasText(mode)) {
                continue;
            }
            mapping.put(mode.trim().toLowerCase(), executor);
        }
        return mapping;
    }
}
