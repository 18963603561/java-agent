package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.application.port.CompressionExecutor;
import com.example.agent.capabilities.context.compression.application.port.CompressionModeResolver;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.domain.policy.CompressionFallbackPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压缩执行路由器测试。
 */
class CompressionExecutionRouterTest {

    @Test
    void shouldRouteToConfiguredModeExecutor() {
        CompressionExecutionRouter router = new CompressionExecutionRouter(
                fixedModeResolver("llm"),
                List.of(new StubExecutor("rule", true), new StubExecutor("llm", false)),
                fallbackPolicy());

        CompressionExecutionResult result = router.execute(new CompressionCommand());

        assertEquals("rule", result.getSource());
        assertTrue(result.isSuccess());
        assertEquals("STUB_FAIL", result.getFailureReason());
        assertTrue(result.isFallbackApplied());
    }

    @Test
    void shouldFallbackToRuleWhenModeExecutorMissing() {
        CompressionExecutionRouter router = new CompressionExecutionRouter(
                fixedModeResolver("hybrid"),
                List.of(new StubExecutor("rule", true)),
                fallbackPolicy());

        CompressionExecutionResult result = router.execute(new CompressionCommand());

        assertEquals("rule", result.getSource());
        assertTrue(result.isSuccess());
        assertEquals("MODE_NOT_SUPPORTED:hybrid", result.getFailureReason());
    }

    @Test
    void shouldReturnFailedWhenNoAvailableExecutor() {
        CompressionExecutionRouter router = new CompressionExecutionRouter(
                fixedModeResolver("llm"),
                List.of(),
                fallbackPolicy());

        CompressionExecutionResult result = router.execute(new CompressionCommand());

        assertFalse(result.isSuccess());
        assertEquals("llm", result.getSource());
        assertEquals("NO_AVAILABLE_EXECUTOR", result.getFailureReason());
    }

    /**
     * 构造固定模式解析器。
     */
    private CompressionModeResolver fixedModeResolver(String mode) {
        return () -> mode;
    }

    /**
     * 构造降级策略。
     */
    private CompressionFallbackPolicy fallbackPolicy() {
        // 降级策略：仅在 llm 失败时触发 rule 回退。
        return (mode, result) -> "llm".equalsIgnoreCase(mode) && (result == null || !result.isSuccess());
    }

    /**
     * 路由测试桩执行器。
     */
    private static class StubExecutor implements CompressionExecutor {

        /**
         * 执行器模式。
         */
        private final String mode;

        /**
         * 是否返回成功。
         */
        private final boolean success;

        private StubExecutor(String mode, boolean success) {
            this.mode = mode;
            this.success = success;
        }

        @Override
        public String mode() {
            return mode;
        }

        @Override
        public CompressionExecutionResult execute(CompressionCommand command) {
            CompressionExecutionResult result = new CompressionExecutionResult();
            result.setSource(mode);
            result.setSuccess(success);
            // 失败注入：用于校验路由返回的执行来源与原因透传。
            if (!success) {
                result.setFailureReason("STUB_FAIL");
            }
            return result;
        }
    }
}

