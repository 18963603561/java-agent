package com.example.agent.capabilities.memory.recall;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.config.MemoryRecallProperties;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.model.RetrievalPriority;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.security.redaction.RedactionService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆召回服务，负责在任务执行前召回会话相关记忆并生成上下文摘要。
 */
@Service
public class MemoryRecallService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRecallService.class);

    /**
     * 召回上下文解析器。
     */
    private final RecallContextResolver recallContextResolver;

    /**
     * 召回策略解析器。
     */
    private final RecallPolicyResolver recallPolicyResolver;

    /**
     * 召回检索执行器。
     */
    private final RecallExecutionPlanner recallExecutionPlanner;

    /**
     * 召回后处理器。
     */
    private final RecallPostProcessor recallPostProcessor;

    /**
     * 召回指标记录器。
     */
    private final RecallMetricsRecorder recallMetricsRecorder;

    /**
     * 脱敏服务。
     */
    private final RedactionService redactionService;

    @Autowired
    public MemoryRecallService(RecallContextResolver recallContextResolver,
                               RecallPolicyResolver recallPolicyResolver,
                               RecallExecutionPlanner recallExecutionPlanner,
                               RecallPostProcessor recallPostProcessor,
                               RecallMetricsRecorder recallMetricsRecorder,
                               RedactionService redactionService) {
        this.recallContextResolver = recallContextResolver;
        this.recallPolicyResolver = recallPolicyResolver;
        this.recallExecutionPlanner = recallExecutionPlanner;
        this.recallPostProcessor = recallPostProcessor;
        this.recallMetricsRecorder = recallMetricsRecorder;
        this.redactionService = redactionService;
    }

    /**
     * 兼容测试与手工构造场景的便捷构造方法。
     *
     * @param memoryStore 记忆存储门面
     * @param properties 召回配置
     * @param redactionService 脱敏服务
     * @param metricsPublisher 指标发布器
     */
    public MemoryRecallService(MemoryStore memoryStore,
                               MemoryRecallProperties properties,
                               RedactionService redactionService,
                               MetricsPublisher metricsPublisher) {
        this(new RecallContextResolver(properties),
                new RecallPolicyResolver(),
                new RecallExecutionPlanner(memoryStore),
                new RecallPostProcessor(redactionService),
                new RecallMetricsRecorder(metricsPublisher),
                redactionService);
    }

    /**
     * 基于任务请求与上下文执行记忆召回。
     *
     * @param request 任务请求
     * @param context 上下文覆盖参数
     * @param tenantContext 租户上下文
     * @return 召回结果
     */
    public MemoryRecallResult recall(TaskRequest request, Map<String, Object> context, TenantContext tenantContext) {
        if (tenantContext == null) {
            return MemoryRecallResult.skipped("tenant_missing");
        }
        RecallContext recallContext = recallContextResolver.resolve(request, context);
        if (!recallContext.isEnabled()) {
            log.debug("记忆召回关闭, tenantId={}", tenantContext.getTenantId());
            return MemoryRecallResult.skipped("disabled");
        }
        if (request == null) {
            return MemoryRecallResult.skipped("request_missing");
        }
        String sessionId = request.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            return MemoryRecallResult.skipped("session_missing");
        }
        String query = request.getQuery();
        if (!StringUtils.hasText(query)) {
            return MemoryRecallResult.skipped("query_empty");
        }

        if (!recallContext.isForce() && query.trim().length() < Math.max(0, recallContext.getMinQueryLength())) {
            return MemoryRecallResult.skipped("query_too_short");
        }

        RecallPolicySnapshot policySnapshot = recallPolicyResolver.resolve(recallContext.getEffectiveContext());
        List<RetrievalPriority> retrievalPriority = policySnapshot.getRetrievalPriority();
        boolean enableSensitiveMask = policySnapshot.isEnableSensitiveMask();
        recallMetricsRecorder.recordRetrievalPriority(retrievalPriority);
        log.info("记忆召回开始, tenantId={}, workflowId={}, sessionId={}, queryLength={}, limit={}, retrievalPriority={}, enableSensitiveMask={}",
                tenantContext.getTenantId(), recallContext.getWorkflowId(), sessionId, query.length(),
                recallContext.getLimit(),
                retrievalPriority, enableSensitiveMask);

        try {
            List<MemoryRecord> records = recallExecutionPlanner.executeSearch(
                    sessionId,
                    query,
                    recallContext.getLimit(),
                    tenantContext,
                    retrievalPriority);
            RecallPostProcessResult postProcessResult = recallPostProcessor.process(
                    records,
                    recallContext.isIncludeCompressed(),
                    recallContext.getMaxRecordChars(),
                    recallContext.getMaxSummaryChars(),
                    enableSensitiveMask);
            if (postProcessResult.getRecords().isEmpty()) {
                log.info("记忆召回无命中, tenantId={}, sessionId={}", tenantContext.getTenantId(), sessionId);
                return MemoryRecallResult.skipped("empty");
            }
            log.info("记忆召回完成, tenantId={}, workflowId={}, sessionId={}, count={}, summaryLength={}, "
                            + "redactionsAppliedCount={}, enabled={}, rejectOnSecrets={}, redactOnPii={}",
                    tenantContext.getTenantId(), recallContext.getWorkflowId(), sessionId,
                    postProcessResult.getRecords().size(),
                    postProcessResult.getSummary() == null ? 0 : postProcessResult.getSummary().length(),
                    postProcessResult.getRedactionsAppliedCount(),
                    redactionService != null && redactionService.isEnabled(),
                    redactionService != null && redactionService.isRejectOnSecrets(),
                    redactionService != null && redactionService.isRedactOnPii());
            return MemoryRecallResult.hit(postProcessResult.getRecords(),
                    postProcessResult.getSummary(),
                    postProcessResult.getRedactionsAppliedCount());
        } catch (Exception ex) {
            log.error("记忆召回异常, tenantId={}, sessionId={}",
                    tenantContext.getTenantId(), sessionId, ex);
            return MemoryRecallResult.skipped("recall_failed");
        }
    }
}
