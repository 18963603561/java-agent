package com.example.agent.governance.common.telemetry;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 治理模块可观测统一词典与上下文封装。
 */
@Component
public class GovernanceTelemetry {

    /**
     * 统一日志字段：domain。
     */
    public static final String FIELD_DOMAIN = "domain";

    /**
     * 统一日志字段：action。
     */
    public static final String FIELD_ACTION = "action";

    /**
     * 统一日志字段：result。
     */
    public static final String FIELD_RESULT = "result";

    /**
     * 统一指标前缀。
     */
    public static final String METRIC_PREFIX = "governance";

    private final MetricsPublisher metricsPublisher;

    public GovernanceTelemetry(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 构建治理日志上下文字段。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param domain 领域名称
     * @param action 动作名称
     * @param result 执行结果
     * @return 日志上下文
     */
    public Map<String, Object> buildContext(TenantContext tenantContext,
                                            String workflowId,
                                            String taskId,
                                            String domain,
                                            String action,
                                            String result) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tenantId", tenantContext != null ? tenantContext.getTenantId() : null);
        context.put("workflowId", workflowId);
        context.put("taskId", taskId);
        context.put("requestId", tenantContext != null ? tenantContext.getRequestId() : null);
        context.put("traceId", tenantContext != null ? tenantContext.getTraceId() : null);
        context.put(FIELD_DOMAIN, domain);
        context.put(FIELD_ACTION, action);
        context.put(FIELD_RESULT, result);
        return context;
    }

    /**
     * 统一治理计数指标。
     *
     * @param metricSuffix 指标后缀
     * @param tags 标签键值对
     */
    public void increment(String metricSuffix, String... tags) {
        if (metricsPublisher == null || !StringUtils.hasText(metricSuffix)) {
            return;
        }
        metricsPublisher.incrementWithTags(METRIC_PREFIX + "." + metricSuffix, tags);
    }

    /**
     * 统一治理分布指标。
     *
     * @param metricSuffix 指标后缀
     * @param value 数值
     * @param tags 标签键值对
     */
    public void summary(String metricSuffix, double value, String... tags) {
        if (metricsPublisher == null || !StringUtils.hasText(metricSuffix)) {
            return;
        }
        metricsPublisher.recordSummary(METRIC_PREFIX + "." + metricSuffix, value, tags);
    }

    /**
     * 统一治理耗时指标。
     *
     * @param metricSuffix 指标后缀
     * @param millis 耗时毫秒
     */
    public void time(String metricSuffix, long millis) {
        if (metricsPublisher == null || !StringUtils.hasText(metricSuffix)) {
            return;
        }
        metricsPublisher.recordTime(METRIC_PREFIX + "." + metricSuffix, millis);
    }

    /**
     * 统一治理指标命名构造。
     *
     * @param domain 领域
     * @param action 动作
     * @param metric 指标名
     * @return 完整指标名
     */
    public String metricName(String domain, String action, String metric) {
        String resolvedDomain = StringUtils.hasText(domain) ? domain.trim() : "unknown";
        String resolvedAction = StringUtils.hasText(action) ? action.trim() : "unknown";
        String resolvedMetric = StringUtils.hasText(metric) ? metric.trim() : "total";
        return METRIC_PREFIX + "." + resolvedDomain + "." + resolvedAction + "." + resolvedMetric;
    }
}

