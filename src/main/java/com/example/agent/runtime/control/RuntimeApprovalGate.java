package com.example.agent.runtime.control;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.output.OutputKeys;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 运行时审批门禁。
 *
 * <p>用途：统一处理审批条件解析、审批请求发布与审批阻塞等待。
 * <p>输入：步骤定义、任务请求、步骤输入、运行时上下文和链路上下文。
 * <p>输出：无。
 * <p>边界：审批拒绝会触发取消异常；评估审批通过后写入运行时标记。
 */
@Component
public class RuntimeApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(RuntimeApprovalGate.class);

    /**
     * 执行控制服务。
     */
    private final ExecutionControlService executionControlService;

    public RuntimeApprovalGate(ExecutionControlService executionControlService) {
        this.executionControlService = executionControlService;
    }

    /**
     * 按条件触发审批并等待决策。
     *
     * <p>输入：步骤定义、请求对象、步骤输入、运行上下文与链路上下文。
     * <p>输出：无。
     * <p>边界：取消时抛出异常并发布取消事件。
     */
    public void requestIfNeeded(StepSpec step,
                                TaskRequest request,
                                Map<String, Object> stepInput,
                                RuntimeContext runtimeContext,
                                String workflowId,
                                TenantContext tenantContext,
                                AtomicLong seqCounter,
                                RuntimeControlEventPublisher eventPublisher) {
        RuntimeApprovalDecision decision = resolveApprovalDecision(step, request, stepInput);
        if (!decision.isExplicit() || !decision.isRequired()) {
            return;
        }
        if (isEvaluationApprovalResolved(decision, runtimeContext)) {
            return;
        }
        ExecutionControlState state = executionControlService.getState(workflowId);
        if (state != ExecutionControlState.WAIT_APPROVAL) {
            Map<String, Object> payload = buildApprovalPayload(step, request, stepInput, decision.getSource());
            executionControlService.requestApproval(workflowId, payload);
            publish(eventPublisher, tenantContext, workflowId, seqCounter, EventType.APPROVAL_REQUESTED, payload);
            log.info("触发审批, tenantId={}, workflowId={}, source={}, stepType={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    decision.getSource(),
                    step != null ? step.getStepType() : null);
        }
        try {
            executionControlService.awaitIfBlocked(workflowId);
        } catch (ErrorCodeException ex) {
            if (isCancelled(ex)) {
                publish(eventPublisher, tenantContext, workflowId, seqCounter, EventType.WORKFLOW_CANCELLED,
                        Map.of("state", ExecutionControlState.CANCELLED.name()));
            }
            throw ex;
        }
        if ("evaluation".equalsIgnoreCase(decision.getSource()) && runtimeContext != null) {
            runtimeContext.setEvaluationApprovalGranted(true);
        }
    }

    private boolean isEvaluationApprovalResolved(RuntimeApprovalDecision decision, RuntimeContext runtimeContext) {
        if (decision == null || runtimeContext == null) {
            return false;
        }
        if (!"evaluation".equalsIgnoreCase(decision.getSource())) {
            return false;
        }
        return runtimeContext.isEvaluationApprovalGranted();
    }

    private RuntimeApprovalDecision resolveApprovalDecision(StepSpec step,
                                                           TaskRequest request,
                                                           Map<String, Object> stepInput) {
        RuntimeApprovalDecision userDecision = resolveApprovalFromUser(request);
        if (userDecision.isExplicit()) {
            return userDecision;
        }
        RuntimeApprovalDecision stepDecision = resolveApprovalFromStep(step);
        if (stepDecision.isExplicit()) {
            return stepDecision;
        }
        RuntimeApprovalDecision evaluationDecision = resolveApprovalFromEvaluation(step, stepInput);
        if (evaluationDecision.isExplicit()) {
            return evaluationDecision;
        }
        return RuntimeApprovalDecision.none();
    }

    private RuntimeApprovalDecision resolveApprovalFromUser(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return RuntimeApprovalDecision.none();
        }
        Map<String, Object> context = request.getContext();
        if (!context.containsKey("requiresApproval")) {
            return RuntimeApprovalDecision.none();
        }
        boolean required = isTruthy(context.get("requiresApproval"));
        return new RuntimeApprovalDecision(true, required, "user");
    }

    private RuntimeApprovalDecision resolveApprovalFromStep(StepSpec step) {
        if (step == null) {
            return RuntimeApprovalDecision.none();
        }
        if (step.getRequiresApproval() != null) {
            String source = normalizeApprovalSource(step.getApprovalSource(), "step");
            if (isEvaluationSource(source)) {
                return RuntimeApprovalDecision.none();
            }
            return new RuntimeApprovalDecision(true, step.getRequiresApproval(), source);
        }
        Map<String, Object> input = resolveStepInput(step);
        if (input != null && input.containsKey("requiresApproval")) {
            String source = normalizeApprovalSource(input.get("approvalSource"), "step");
            if (isEvaluationSource(source)) {
                return RuntimeApprovalDecision.none();
            }
            boolean required = isTruthy(input.get("requiresApproval"));
            return new RuntimeApprovalDecision(true, required, source);
        }
        return RuntimeApprovalDecision.none();
    }

    private RuntimeApprovalDecision resolveApprovalFromEvaluation(StepSpec step, Map<String, Object> stepInput) {
        Object value = null;
        String source = null;
        Map<String, Object> input = resolveStepInput(step);
        if (step != null && step.getRequiresApproval() != null) {
            String stepSource = normalizeApprovalSource(step.getApprovalSource(), "evaluation");
            if (isEvaluationSource(stepSource)) {
                value = step.getRequiresApproval();
                source = stepSource;
            }
        }
        if (source == null && input != null && input.containsKey("requiresApproval")) {
            String inputSource = normalizeApprovalSource(input.get("approvalSource"), "evaluation");
            if (isEvaluationSource(inputSource)) {
                value = input.get("requiresApproval");
                source = inputSource;
            }
        }
        if (source == null && stepInput != null && stepInput.containsKey("requiresApproval")) {
            value = stepInput.get("requiresApproval");
            source = normalizeApprovalSource(stepInput.get("approvalSource"), "evaluation");
        }
        if (source == null && stepInput != null && stepInput.get("context") instanceof Map<?, ?> contextMap
                && contextMap.containsKey("requiresApproval")) {
            value = contextMap.get("requiresApproval");
            source = normalizeApprovalSource(contextMap.get("approvalSource"), "evaluation");
        }
        if (source == null) {
            return RuntimeApprovalDecision.none();
        }
        boolean required = isTruthy(value);
        return new RuntimeApprovalDecision(true, required, source);
    }

    private Map<String, Object> resolveStepInput(StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }

    private boolean isEvaluationSource(String source) {
        return "evaluation".equalsIgnoreCase(source);
    }

    private String normalizeApprovalSource(Object source, String fallback) {
        if (source instanceof String text && !text.isBlank()) {
            return text.trim().toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 构造审批事件载荷摘要。
     */
    private Map<String, Object> buildApprovalPayload(StepSpec step,
                                                     TaskRequest request,
                                                     Map<String, Object> stepInput,
                                                     String approvalSource) {
        Map<String, Object> payload = new HashMap<>();
        if (step != null && step.getStepType() != null) {
            payload.put("stepType", step.getStepType());
        }
        if (stepInput != null) {
            Object toolName = stepInput.get(OutputKeys.TOOL);
            if (toolName == null) {
                toolName = stepInput.get(OutputKeys.TOOL_NAME);
            }
            if (toolName instanceof String value && !value.isBlank()) {
                payload.put(OutputKeys.TOOL_NAME, value);
            }
            payload.put("inputKeys", stepInput.keySet());
            payload.put("inputSize", stepInput.size());
            Object query = stepInput.get("query");
            if (query instanceof String value && !value.isBlank()) {
                payload.put("query", truncate(value, 200));
            }
        } else if (request != null && request.getQuery() != null) {
            payload.put("query", truncate(request.getQuery(), 200));
        }
        payload.put("approval", "required");
        payload.put("approvalSource", approvalSource);
        payload.put("status", "PENDING_APPROVAL");
        return payload;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void publish(RuntimeControlEventPublisher eventPublisher,
                         TenantContext tenantContext,
                         String workflowId,
                         AtomicLong seqCounter,
                         EventType type,
                         Map<String, Object> payload) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publish(tenantContext, workflowId, seqCounter, type, payload);
    }

    private boolean isCancelled(ErrorCodeException ex) {
        return ex != null && "CANCELLED".equals(ex.getErrorCode());
    }
}
