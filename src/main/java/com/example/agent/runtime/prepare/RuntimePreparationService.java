package com.example.agent.runtime.prepare;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.capabilities.context.ContextBuildRequest;
import com.example.agent.capabilities.context.ContextBuildResult;
import com.example.agent.capabilities.context.ContextBuilder;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.evidence.EvidencePackService;
import com.example.agent.capabilities.context.builder.exception.ContextBuildException;
import com.example.agent.capabilities.context.runtime.ContextRuntimeViews;
import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import com.example.agent.capabilities.context.runtime.MutableContextRuntimeView;
import com.example.agent.capabilities.memory.recall.MemoryRecallResult;
import com.example.agent.capabilities.memory.recall.MemoryRecallService;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.runtime.step.RuntimeContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 运行时准备服务。
 *
 * <p>用途：集中处理运行前置逻辑，包含上下文合并、记忆召回、证据同步与快照构建。
 * <p>输入：任务请求、租户上下文、工作流标识与任务标识。
 * <p>输出：可用于主编排链路的准备结果。
 * <p>边界：任一外部依赖调用失败时保持原有失败语义并向上抛出异常。
 */
@Service
public class RuntimePreparationService {

    private static final Logger log = LoggerFactory.getLogger(RuntimePreparationService.class);

    /**
     * 记忆召回服务。
     */
    private final MemoryRecallService memoryRecallService;

    /**
     * 钩子管理器。
     */
    private final HookManager hookManager;

    /**
     * 证据包服务。
     */
    private final EvidencePackService evidencePackService;

    /**
     * 上下文构建器。
     */
    private final ContextBuilder contextBuilder;

    /**
     * 上下文事件发布器。
     */
    private final ContextEventPublisher contextEventPublisher;

    public RuntimePreparationService(MemoryRecallService memoryRecallService,
                                     HookManager hookManager,
                                     EvidencePackService evidencePackService,
                                     ContextBuilder contextBuilder,
                                     ContextEventPublisher contextEventPublisher) {
        this.memoryRecallService = memoryRecallService;
        this.hookManager = hookManager;
        this.evidencePackService = evidencePackService;
        this.contextBuilder = contextBuilder;
        this.contextEventPublisher = contextEventPublisher;
    }

    /**
     * 执行运行时准备。
     *
     * <p>输入：任务请求、租户上下文、工作流标识、任务标识与序列计数器。
     * <p>输出：包含有效请求与运行上下文的准备结果。
     * <p>边界：外部依赖异常会记录日志并继续向上抛出。
     */
    public RuntimePreparationResult prepare(TaskRequest request,
                                            TenantContext tenantContext,
                                            String workflowId,
                                            String taskId,
                                            AtomicLong seqCounter) {
        log.info("运行时准备开始, tenantId={}, workflowId={}, taskId={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                taskId);
        RuntimeContext runtimeContext = initRuntimeContext(request, workflowId);
        MemoryRecallResult recallResult;
        try {
            recallResult = memoryRecallService.recall(request, runtimeContext.asMap(), tenantContext);
            log.debug("运行时记忆召回完成, tenantId={}, workflowId={}, used={}, count={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    recallResult != null && recallResult.isUsed(),
                    recallResult != null ? recallResult.getCount() : 0);
        } catch (RuntimeException ex) {
            log.error("运行时记忆召回失败, tenantId={}, workflowId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    ex);
            throw ex;
        }
        applyMemoryContext(runtimeContext.asMap(), recallResult);
        if (hookManager != null) {
            hookManager.postRecall(tenantContext, workflowId, buildRecallHookPayload(workflowId, recallResult));
        }
        syncEvidencePackFromStore(runtimeContext.asMap(), tenantContext, workflowId);
        ContextBuildResult buildResult = buildContextSnapshot(request, tenantContext, workflowId, taskId,
                recallResult, runtimeContext.asMap(), seqCounter);
        applyContextSnapshot(runtimeContext.asMap(), buildResult);
        if (hookManager != null) {
            hookManager.postTrim(tenantContext, workflowId, buildTrimHookPayload(runtimeContext.asMap(), workflowId, buildResult));
        }
        TaskRequest effectiveRequest = buildRequestWithContext(request, runtimeContext.asMap());
        log.info("运行时准备结束, tenantId={}, workflowId={}, contextKeys={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                runtimeContext.asMap().keySet());
        return new RuntimePreparationResult(effectiveRequest, runtimeContext);
    }

    private RuntimeContext initRuntimeContext(TaskRequest request, String workflowId) {
        Map<String, Object> runtimeContext = new HashMap<>();
        MutableContextRuntimeView runtimeView = ContextRuntimeViews.mutable(runtimeContext, log, null);
        if (request != null && request.getContext() != null) {
            runtimeContext.putAll(request.getContext());
        }
        if (request != null && request.getToolChoice() != null) {
            runtimeView.putToolChoice(request.getToolChoice());
        }
        runtimeView.putWorkflowIdIfAbsent(workflowId);
        return new RuntimeContext(runtimeContext);
    }

    /**
     * 将记忆召回结果注入运行上下文，供规划与工具使用。
     */
    private void applyMemoryContext(Map<String, Object> runtimeContext, MemoryRecallResult recallResult) {
        if (runtimeContext == null || recallResult == null || !recallResult.isUsed()) {
            return;
        }
        MutableContextRuntimeView runtimeView = ContextRuntimeViews.mutable(runtimeContext, log, null);
        Map<String, Object> memoryContext = new HashMap<>();
        memoryContext.put("summary", recallResult.getSummary());
        memoryContext.put("records", recallResult.getRecords());
        memoryContext.put("count", recallResult.getCount());
        memoryContext.put("reason", recallResult.getReason());
        runtimeView.putMemory(memoryContext);
    }

    /**
     * 构建记忆召回 Hook 载荷。
     */
    private Map<String, Object> buildRecallHookPayload(String workflowId, MemoryRecallResult recallResult) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workflowId", workflowId);
        if (recallResult == null) {
            payload.put("count", 0);
            payload.put("reason", "recall_missing");
            return payload;
        }
        payload.put("count", recallResult.getCount());
        payload.put("reason", recallResult.getReason());
        if (recallResult.getRecords() != null && !recallResult.getRecords().isEmpty()) {
            payload.put("records", recallResult.getRecords());
        }
        return payload;
    }

    /**
     * 构建上下文裁剪 Hook 载荷。
     */
    private Map<String, Object> buildTrimHookPayload(Map<String, Object> runtimeContext,
                                                     String workflowId,
                                                     ContextBuildResult buildResult) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workflowId", workflowId);
        if (runtimeContext != null) {
            Object snapshotId = runtimeContext.get("snapshotId");
            if (snapshotId != null) {
                payload.put("snapshotId", snapshotId);
            }
        }
        ContextTrimReport trimReport = buildResult != null ? buildResult.getTrimReport() : null;
        if (trimReport == null) {
            payload.put("summary", "trim_not_triggered");
            return payload;
        }
        payload.put("beforeTokens", trimReport.getTotalBeforeTokens());
        payload.put("afterTokens", trimReport.getTotalAfterTokens());
        payload.put("reasons", trimReport.getReasons());
        payload.put("summary", "trimmed");
        if (trimReport.getRemovedItemsBySection() != null) {
            payload.put("removedBySection", trimReport.getRemovedItemsBySection());
        }
        return payload;
    }

    /**
     * 从证据包服务同步当前工作流证据到运行上下文。
     */
    private void syncEvidencePackFromStore(Map<String, Object> runtimeContext,
                                           TenantContext tenantContext,
                                           String workflowId) {
        if (runtimeContext == null || evidencePackService == null || tenantContext == null
                || workflowId == null || workflowId.isBlank()) {
            return;
        }
        MutableContextRuntimeView runtimeView = ContextRuntimeViews.mutable(runtimeContext, log, null);
        try {
            EvidencePack pack = evidencePackService.getPack(tenantContext.getTenantId(), workflowId);
            if (pack != null) {
                runtimeView.putEvidencePack(pack);
            }
        } catch (RuntimeException ex) {
            log.error("证据包同步失败, tenantId={}, workflowId={}",
                    tenantContext.getTenantId(),
                    workflowId,
                    ex);
            throw ex;
        }
    }

    /**
     * 构建上下文快照并发布事件。
     */
    private ContextBuildResult buildContextSnapshot(TaskRequest request,
                                                    TenantContext tenantContext,
                                                    String workflowId,
                                                    String taskId,
                                                    MemoryRecallResult recallResult,
                                                    Map<String, Object> runtimeContext,
                                                    AtomicLong seqCounter) {
        if (contextBuilder == null) {
            return null;
        }
        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId(workflowId);
        buildRequest.setTaskId(taskId);
        buildRequest.setRecallResult(recallResult);
        buildRequest.setRuntimeContext(runtimeContext);
        try {
            ContextBuildResult result = contextBuilder.build(buildRequest);
            if (result != null && result.getSnapshot() != null && contextEventPublisher != null) {
                contextEventPublisher.publishSnapshot(tenantContext, workflowId, seqCounter,
                        result.getSnapshot(), result.getBudgetAllocation(), result.getPruneResult(), result.getMetrics());
            }
            return result;
        } catch (ContextBuildException ex) {
            log.error("上下文快照构建失败, tenantId={}, workflowId={}, taskId={}, stage={}, errorCode={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    taskId,
                    ex.getStage(),
                    ex.getErrorCode(),
                    ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("上下文快照构建失败, tenantId={}, workflowId={}, taskId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    taskId,
                    ex);
            throw ex;
        }
    }

    /**
     * 将上下文快照写入运行时上下文。
     */
    private void applyContextSnapshot(Map<String, Object> runtimeContext, ContextBuildResult buildResult) {
        if (runtimeContext == null || buildResult == null) {
            return;
        }
        MutableContextRuntimeView runtimeView = ContextRuntimeViews.mutable(runtimeContext, log, null);
        if (buildResult.getSnapshot() != null) {
            runtimeView.putContextSnapshot(buildResult.getSnapshot());
            String snapshotId = buildResult.getSnapshot().getSnapshotId();
            if (snapshotId != null && !snapshotId.isBlank()) {
                runtimeView.putSnapshotIdIfAbsent(snapshotId);
                Object evidenceObj = runtimeContext.get(ContextRuntimeKeys.EVIDENCE_PACK);
                if (evidenceObj instanceof EvidencePack pack
                        && (pack.getSnapshotId() == null || pack.getSnapshotId().isBlank())) {
                    pack.setSnapshotId(snapshotId);
                }
            }
        }
        if (buildResult.getBudgetAllocation() != null) {
            runtimeView.putContextBudget(buildResult.getBudgetAllocation());
        }
        if (buildResult.getPruneResult() != null) {
            runtimeView.putContextPrune(buildResult.getPruneResult());
        }
    }

    /**
     * 构造携带运行上下文的任务请求副本，避免修改原请求对象。
     */
    private TaskRequest buildRequestWithContext(TaskRequest request, Map<String, Object> runtimeContext) {
        if (request == null) {
            return null;
        }
        TaskRequest copy = new TaskRequest();
        copy.setQuery(request.getQuery());
        copy.setSessionId(request.getSessionId());
        copy.setSkillName(request.getSkillName());
        copy.setIdempotencyKey(request.getIdempotencyKey());
        copy.setToolChoice(request.getToolChoice());
        copy.setContext(runtimeContext);
        return copy;
    }
}


