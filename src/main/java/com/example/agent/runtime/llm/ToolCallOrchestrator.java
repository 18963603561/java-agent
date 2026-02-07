package com.example.agent.runtime.llm;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 工具调用编排器。
 *
 * <p>用途：统一封装工具调用重试、错误码映射与可重试判定逻辑。
 * <p>输入：任务请求、链路上下文、工具名称与工具参数。
 * <p>输出：工具调用结果对象（成功或失败）。
 * <p>边界：重试达到上限后返回失败结果，不向上抛出业务异常。
 */
@Service
public class ToolCallOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ToolCallOrchestrator.class);

    /**
     * 工具状态：成功。
     */
    public static final String TOOL_STATUS_SUCCESS = "SUCCESS";

    /**
     * 工具状态：失败。
     */
    public static final String TOOL_STATUS_FAILED = "FAILED";

    /**
     * 工具错误：请求非法。
     */
    public static final String TOOL_INVALID_REQUEST = "TOOL_INVALID_REQUEST";

    /**
     * 工具错误：未找到。
     */
    public static final String TOOL_NOT_FOUND = "TOOL_NOT_FOUND";

    /**
     * 工具错误：超时。
     */
    public static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";

    /**
     * 工具错误：限流。
     */
    public static final String TOOL_RATE_LIMITED = "TOOL_RATE_LIMITED";

    /**
     * 工具错误：不可用。
     */
    public static final String TOOL_UNAVAILABLE = "TOOL_UNAVAILABLE";

    /**
     * 工具错误：执行失败。
     */
    public static final String TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED";

    private final EnforcementGateway enforcementGateway;

    /**
     * 工具调用最大重试次数。
     */
    @Value("${agent.llm-step.tool-retry.max-attempts:2}")
    private int toolMaxAttempts;

    /**
     * 工具调用重试基础延迟（毫秒）。
     */
    @Value("${agent.llm-step.tool-retry.base-delay-ms:100}")
    private long toolBaseDelayMs;

    /**
     * 工具调用重试最大延迟（毫秒）。
     */
    @Value("${agent.llm-step.tool-retry.max-delay-ms:1000}")
    private long toolMaxDelayMs;

    /**
     * 工具调用重试抖动比例。
     */
    @Value("${agent.llm-step.tool-retry.jitter-ratio:0.2}")
    private double toolJitterRatio;

    public ToolCallOrchestrator(EnforcementGateway enforcementGateway) {
        this.enforcementGateway = enforcementGateway;
    }

    /**
     * 执行工具调用。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @return 工具调用结果
     */
    public ToolCallOutcome executeToolCall(TaskRequest request,
                                           TenantContext tenantContext,
                                           String workflowId,
                                           String taskId,
                                           AtomicLong seqCounter,
                                           String toolName,
                                           Map<String, Object> toolArguments) {
        RetryPolicy retryPolicy = new RetryPolicy(toolBaseDelayMs, toolMaxDelayMs, toolJitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.info("LLM 步骤工具调用开始, workflowId={}, tool={}, attempt={}",
                        workflowId, toolName, attempt);
                Map<String, Object> result = enforcementGateway.executeWithArguments(
                        request, tenantContext, workflowId, taskId, seqCounter, toolName, toolArguments);
                log.info("LLM 步骤工具调用成功, workflowId={}, tool={}, attempt={}",
                        workflowId, toolName, attempt);
                return ToolCallOutcome.success(result);
            } catch (ErrorCodeException ex) {
                String mapped = mapToolErrorCode(ex);
                boolean retryable = isRetryableToolCode(mapped);
                log.warn("LLM 步骤工具调用失败, workflowId={}, tool={}, attempt={}, errorCode={}",
                        workflowId, toolName, attempt, mapped, ex);
                if (retryable && attempt < toolMaxAttempts) {
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                return ToolCallOutcome.failure(mapped, ex.getReason(), retryable);
            } catch (Exception ex) {
                String mapped = TOOL_EXECUTION_FAILED;
                boolean retryable = isRetryableToolCode(mapped);
                log.error("LLM 步骤工具调用异常, workflowId={}, tool={}, attempt={}",
                        workflowId, toolName, attempt, ex);
                if (retryable && attempt < toolMaxAttempts) {
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                return ToolCallOutcome.failure(mapped, ex.getMessage(), retryable);
            }
        }
    }

    /**
     * 错误码映射。
     *
     * @param ex 异常
     * @return 统一错误码
     */
    public String mapToolErrorCode(ErrorCodeException ex) {
        if (ex == null) {
            return TOOL_EXECUTION_FAILED;
        }
        int status = ex.getStatusCode() != null ? ex.getStatusCode().value() : -1;
        String code = ex.getErrorCode();
        if (status == HttpStatus.BAD_REQUEST.value() || "INVALID_REQUEST".equalsIgnoreCase(code)) {
            return TOOL_INVALID_REQUEST;
        }
        if (status == HttpStatus.NOT_FOUND.value() || "NOT_FOUND".equalsIgnoreCase(code)) {
            return TOOL_NOT_FOUND;
        }
        if (status == HttpStatus.REQUEST_TIMEOUT.value() || status == HttpStatus.GATEWAY_TIMEOUT.value()
                || "TIMEOUT".equalsIgnoreCase(code)) {
            return TOOL_TIMEOUT;
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value() || "RATE_LIMITED".equalsIgnoreCase(code)) {
            return TOOL_RATE_LIMITED;
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE.value() || "MCP_UNAVAILABLE".equalsIgnoreCase(code)
                || "CIRCUIT_OPEN".equalsIgnoreCase(code)) {
            return TOOL_UNAVAILABLE;
        }
        return TOOL_EXECUTION_FAILED;
    }

    /**
     * 是否可重试。
     *
     * @param code 错误码
     * @return 是否可重试
     */
    public boolean isRetryableToolCode(String code) {
        return TOOL_TIMEOUT.equals(code) || TOOL_RATE_LIMITED.equals(code) || TOOL_UNAVAILABLE.equals(code);
    }

    /**
     * 工具调用结果。
     */
    public static final class ToolCallOutcome {
        private final String status;
        private final Map<String, Object> result;
        private final String errorCode;
        private final String errorMessage;
        private final boolean retryable;

        private ToolCallOutcome(String status,
                                Map<String, Object> result,
                                String errorCode,
                                String errorMessage,
                                boolean retryable) {
            this.status = status;
            this.result = result;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.retryable = retryable;
        }

        /**
         * 成功结果。
         *
         * @param result 原始结果
         * @return 结果
         */
        public static ToolCallOutcome success(Map<String, Object> result) {
            return new ToolCallOutcome(TOOL_STATUS_SUCCESS, result, null, null, false);
        }

        /**
         * 失败结果。
         *
         * @param errorCode 错误码
         * @param errorMessage 错误消息
         * @param retryable 是否可重试
         * @return 结果
         */
        public static ToolCallOutcome failure(String errorCode, String errorMessage, boolean retryable) {
            return new ToolCallOutcome(TOOL_STATUS_FAILED, null, errorCode, errorMessage, retryable);
        }

        public String getStatus() {
            return status;
        }

        public Map<String, Object> getResult() {
            return result;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
