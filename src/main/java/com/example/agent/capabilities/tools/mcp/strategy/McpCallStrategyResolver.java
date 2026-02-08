package com.example.agent.capabilities.tools.mcp.strategy;

import com.example.agent.common.error.ErrorCodeException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MCP 调用策略解析器。
 */
@Component
public class McpCallStrategyResolver {

    /**
     * 解析策略配置。
     *
     * @param strategyValue 配置值
     * @return 调用策略
     */
    public McpCallStrategy resolve(String strategyValue) {
        if (!StringUtils.hasText(strategyValue)) {
            return McpCallStrategy.REMOTE_FIRST;
        }
        String normalized = strategyValue.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "remote-only" -> McpCallStrategy.REMOTE_ONLY;
            case "local-only" -> McpCallStrategy.LOCAL_ONLY;
            case "local-first" -> McpCallStrategy.LOCAL_FIRST;
            case "remote-first" -> McpCallStrategy.REMOTE_FIRST;
            default -> McpCallStrategy.REMOTE_FIRST;
        };
    }

    /**
     * 判断是否允许重试。
     *
     * @param ex 业务异常
     * @return 是否可重试
     */
    public boolean isRetryable(ErrorCodeException ex) {
        if (ex == null) {
            return false;
        }
        String code = ex.getErrorCode();
        return "MCP_UNAVAILABLE".equals(code) || "CIRCUIT_OPEN".equals(code);
    }

    /**
     * 判断是否允许回退到本地。
     *
     * @param ex 业务异常
     * @return 是否允许回退
     */
    public boolean isFallbackTrigger(ErrorCodeException ex) {
        if (ex == null) {
            return false;
        }
        String code = ex.getErrorCode();
        if ("MCP_UNAVAILABLE".equals(code) || "CIRCUIT_OPEN".equals(code) || "NOT_FOUND".equals(code)) {
            return true;
        }
        return isToolNotFoundReason(ex.getReason());
    }

    /**
     * 判断错误原因是否表示工具不存在。
     *
     * @param reason 错误原因
     * @return 是否表示工具不存在
     */
    public boolean isToolNotFoundReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("tool not found")
                || lower.contains("not found")
                || reason.contains("工具不存在");
    }
}

