package com.example.agent.capabilities.tools.execution.service;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.observability.TracingPublisher;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * 工具执行追踪服务。
 *
 * <p>用途：统一处理执行链路的 traceId、日志和指标记录。</p>
 */
@Component
public class ToolExecutionTracer {

    /**
     * 解析追踪标识。
     *
     * @param tenantContext 租户上下文
     * @param tracingPublisher 追踪发布器
     * @return traceId
     */
    public String resolveTraceId(TenantContext tenantContext, TracingPublisher tracingPublisher) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher != null ? tracingPublisher.currentTraceId() : null;
    }

    /**
     * 记录调用成功指标。
     *
     * @param metricsPublisher 指标发布器
     * @param traceId 链路追踪标识
     * @param durationMs 耗时
     */
    public void recordCallSuccess(MetricsPublisher metricsPublisher, String traceId, long durationMs) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.increment("tool.call.count", traceId);
        metricsPublisher.recordTime("tool.call.latency.ms", durationMs, traceId);
    }

    /**
     * 记录调用失败指标。
     *
     * @param metricsPublisher 指标发布器
     * @param traceId 链路追踪标识
     */
    public void recordCallFailure(MetricsPublisher metricsPublisher, String traceId) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.increment("tool.call.failure.count", traceId);
    }

    /**
     * 记录缓存命中日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param usageId 计量幂等标识
     * @param traceId 链路标识
     */
    public void logCacheHit(Logger log, String tenantId, String toolName, String usageId, String traceId) {
        if (log == null) {
            return;
        }
        log.info("宸ュ叿缂撳瓨鍛戒腑, tenantId={}, tool={}, usageId={}, traceId={}", tenantId, toolName, usageId, traceId);
    }

    /**
     * 记录执行开始日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param attempt 重试次数
     * @param usageId 计量幂等标识
     * @param traceId 链路标识
     */
    public void logExecutionStart(Logger log,
                                  String tenantId,
                                  String toolName,
                                  int attempt,
                                  String usageId,
                                  String traceId) {
        if (log == null) {
            return;
        }
        log.info("宸ュ叿鎵ц寮€濮? tenantId={}, tool={}, attempt={}, usageId={}, traceId={}",
                tenantId, toolName, attempt, usageId, traceId);
    }

    /**
     * 记录执行完成日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param usageId 计量幂等标识
     * @param traceId 链路标识
     */
    public void logExecutionSuccess(Logger log,
                                    String tenantId,
                                    String toolName,
                                    String usageId,
                                    String traceId) {
        if (log == null) {
            return;
        }
        log.info("宸ュ叿鎵ц瀹屾垚, tenantId={}, tool={}, usageId={}, traceId={}", tenantId, toolName, usageId, traceId);
    }

    /**
     * 记录可重试错误日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param attempt 重试次数
     * @param errorCode 错误码
     * @param traceId 链路标识
     */
    public void logRetryableError(Logger log,
                                  String tenantId,
                                  String toolName,
                                  int attempt,
                                  String errorCode,
                                  String traceId) {
        if (log == null) {
            return;
        }
        log.warn("宸ュ叿鎵ц鍙噸璇? tenantId={}, tool={}, attempt={}, errorCode={}, traceId={}",
                tenantId, toolName, attempt, errorCode, traceId);
    }

    /**
     * 记录不可重试业务异常日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param attempt 重试次数
     * @param errorCode 错误码
     * @param traceId 链路标识
     * @param ex 异常
     */
    public void logBusinessFailure(Logger log,
                                   String tenantId,
                                   String toolName,
                                   int attempt,
                                   String errorCode,
                                   String traceId,
                                   Exception ex) {
        if (log == null) {
            return;
        }
        log.error("宸ュ叿鎵ц澶辫触, tenantId={}, tool={}, attempt={}, errorCode={}, traceId={}",
                tenantId, toolName, attempt, errorCode, traceId, ex);
    }

    /**
     * 记录系统异常可重试日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param attempt 重试次数
     * @param traceId 链路标识
     * @param ex 异常
     */
    public void logSystemRetry(Logger log,
                               String tenantId,
                               String toolName,
                               int attempt,
                               String traceId,
                               Exception ex) {
        if (log == null) {
            return;
        }
        log.warn("宸ュ叿鎵ц寮傚父鍙噸璇? tenantId={}, tool={}, attempt={}, traceId={}",
                tenantId, toolName, attempt, traceId, ex);
    }

    /**
     * 记录系统异常失败日志。
     *
     * @param log 日志器
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @param attempt 重试次数
     * @param traceId 链路标识
     * @param ex 异常
     */
    public void logSystemFailure(Logger log,
                                 String tenantId,
                                 String toolName,
                                 int attempt,
                                 String traceId,
                                 Exception ex) {
        if (log == null) {
            return;
        }
        log.error("宸ュ叿鎵ц寮傚父, tenantId={}, tool={}, attempt={}, traceId={}",
                tenantId, toolName, attempt, traceId, ex);
    }

    /**
     * 输出结构化返回字段。
     *
     * @param toolName 工具名
     * @param result 执行结果
     * @return 调试信息
     */
    public Map<String, Object> buildDebugPayload(String toolName, Map<String, Object> result) {
        return Map.of("tool", toolName, "hasResult", result != null && !result.isEmpty());
    }
}

