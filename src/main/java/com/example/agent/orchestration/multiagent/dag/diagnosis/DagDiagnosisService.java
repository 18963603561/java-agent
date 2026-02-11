package com.example.agent.orchestration.multiagent.dag.diagnosis;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditSnapshot;
import com.example.agent.orchestration.multiagent.dag.audit.DagBackpressureRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagDependencyEventRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagNodeAttemptRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DAG 诊断服务。
 *
 * <p>用途：根据审计快照计算关键路径、阻塞热点与失败根因分布。</p>
 */
@Service
public class DagDiagnosisService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagDiagnosisService.class);

    /**
     * 审计服务。
     */
    private final DagAuditService dagAuditService;

    public DagDiagnosisService(DagAuditService dagAuditService) {
        this.dagAuditService = dagAuditService;
    }

    /**
     * 生成 DAG 诊断报告。
     *
     * @param workflowId 工作流标识
     * @param dagRunId DAG运行标识
     * @return 诊断报告
     */
    public DagDiagnosisReport diagnose(String workflowId, String dagRunId) {
        // 关键逻辑：入参为空直接拒绝，避免跨工作流误诊断。
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(dagRunId)) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "DAG_DIAGNOSIS_INVALID_REQUEST", "诊断参数不完整");
        }
        // 关键逻辑：读取审计快照作为唯一诊断数据源。
        Optional<DagAuditSnapshot> snapshotOptional = dagAuditService.findSnapshot(dagRunId);
        // 关键逻辑：未找到快照时说明运行不存在，返回 404。
        if (snapshotOptional.isEmpty()) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "DAG_RUN_NOT_FOUND", "DAG运行记录不存在");
        }
        DagAuditSnapshot snapshot = snapshotOptional.get();
        DagDiagnosisReport report = new DagDiagnosisReport();
        report.setWorkflowId(workflowId);
        report.setDagRunId(dagRunId);

        DagRunAuditRecord runRecord = snapshot.getRunRecord();
        // 关键逻辑：主记录存在时补齐基础运行态字段。
        if (runRecord != null) {
            report.setStatus(runRecord.getStatus());
            report.setFailurePolicy(runRecord.getFailurePolicy());
        }

        // 关键逻辑：根据尝试耗时计算关键路径热点节点。
        report.setCriticalPathNodeId(resolveCriticalPathNode(snapshot.getAttempts()));
        // 关键逻辑：根据依赖等待事件聚合最长等待节点。
        report.setLongestWaitNodeId(resolveLongestWaitNode(snapshot.getDependencyEvents()));
        // 关键逻辑：根据重试尝试统计重试热点节点。
        report.setRetryHotspotNodeId(resolveRetryHotspotNode(snapshot.getAttempts()));
        // 关键逻辑：根据背压记录统计拥塞热点节点。
        report.setBackpressureHotspotNodeId(resolveBackpressureHotspotNode(snapshot.getBackpressureRecords()));
        // 关键逻辑：聚合失败原因分布用于失败归因分析。
        report.setFailureReasonDistribution(resolveFailureReasonDistribution(snapshot.getAttempts()));
        // 关键逻辑：聚合阻塞原因分布用于容量诊断。
        report.setBlockingReasonDistribution(resolveBlockingReasonDistribution(snapshot.getBackpressureRecords()));
        // 关键逻辑：根据热点与原因分布生成建议动作。
        report.setRecommendations(buildRecommendations(report));

        log.info("DAG诊断完成, workflowId={}, dagRunId={}, criticalPathNodeId={}, retryHotspotNodeId={}",
                workflowId,
                dagRunId,
                report.getCriticalPathNodeId(),
                report.getRetryHotspotNodeId());
        return report;
    }

    /**
     * 计算关键路径热点节点。
     */
    private String resolveCriticalPathNode(List<DagNodeAttemptRecord> attempts) {
        Map<String, Long> durationByNode = new HashMap<>();
        // 关键逻辑：累计每个节点的尝试耗时，用于估算关键路径热点。
        for (DagNodeAttemptRecord attempt : safeList(attempts)) {
            if (attempt == null || !StringUtils.hasText(attempt.getNodeId())) {
                continue;
            }
            long duration = Math.max(0L, attempt.getDurationMs());
            durationByNode.merge(attempt.getNodeId(), duration, Long::sum);
        }
        return durationByNode.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    /**
     * 计算最长等待节点。
     */
    private String resolveLongestWaitNode(List<DagDependencyEventRecord> dependencyEvents) {
        Map<String, Long> waitScoreByNode = new HashMap<>();
        // 关键逻辑：对 TIMEOUT/RETRYING 事件按队列与容量估算等待压力。
        for (DagDependencyEventRecord event : safeList(dependencyEvents)) {
            if (event == null || !StringUtils.hasText(event.getToNode())) {
                continue;
            }
            String status = StringUtils.hasText(event.getDeliveryStatus())
                    ? event.getDeliveryStatus().toUpperCase()
                    : "";
            if (!"TIMEOUT".equals(status) && !"RETRYING".equals(status) && !"REJECTED".equals(status)) {
                continue;
            }
            long score = Math.max(1, event.getQueueSize()) + Math.max(1, event.getCapacity());
            waitScoreByNode.merge(event.getToNode(), score, Long::sum);
        }
        return waitScoreByNode.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    /**
     * 计算重试热点节点。
     */
    private String resolveRetryHotspotNode(List<DagNodeAttemptRecord> attempts) {
        Map<String, Integer> retryCountByNode = new HashMap<>();
        // 关键逻辑：统计 RE TRYING 状态次数，反映重试压力集中点。
        for (DagNodeAttemptRecord attempt : safeList(attempts)) {
            if (attempt == null || !StringUtils.hasText(attempt.getNodeId())) {
                continue;
            }
            String status = StringUtils.hasText(attempt.getStatus()) ? attempt.getStatus().toUpperCase() : "";
            if (!"RETRYING".equals(status)) {
                continue;
            }
            retryCountByNode.merge(attempt.getNodeId(), 1, Integer::sum);
        }
        return retryCountByNode.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    /**
     * 计算背压热点节点。
     */
    private String resolveBackpressureHotspotNode(List<DagBackpressureRecord> backpressureRecords) {
        Map<String, Long> pressureByNode = new HashMap<>();
        // 关键逻辑：累计背压延迟时长，识别最严重拥塞节点。
        for (DagBackpressureRecord record : safeList(backpressureRecords)) {
            if (record == null || !StringUtils.hasText(record.getNodeId())) {
                continue;
            }
            long score = Math.max(1L, record.getDelayMs());
            pressureByNode.merge(record.getNodeId(), score, Long::sum);
        }
        return pressureByNode.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    /**
     * 聚合失败原因分布。
     */
    private Map<String, Integer> resolveFailureReasonDistribution(List<DagNodeAttemptRecord> attempts) {
        Map<String, Integer> distribution = new HashMap<>();
        // 关键逻辑：仅统计失败态尝试，避免重试中间态干扰失败分布。
        for (DagNodeAttemptRecord attempt : safeList(attempts)) {
            if (attempt == null) {
                continue;
            }
            String status = StringUtils.hasText(attempt.getStatus()) ? attempt.getStatus().toUpperCase() : "";
            if (!"FAILED".equals(status)) {
                continue;
            }
            String reasonCode = normalizeFailureReasonCode(attempt.getReasonCode());
            distribution.merge(reasonCode, 1, Integer::sum);
        }
        return sortByValueDesc(distribution);
    }

    /**
     * 聚合阻塞原因分布。
     */
    private Map<String, Integer> resolveBlockingReasonDistribution(List<DagBackpressureRecord> backpressureRecords) {
        Map<String, Integer> distribution = new HashMap<>();
        // 关键逻辑：按背压原因聚合次数，识别最常见阻塞类型。
        for (DagBackpressureRecord record : safeList(backpressureRecords)) {
            if (record == null) {
                continue;
            }
            String reason = normalizeBlockingReasonCode(record.getReason());
            distribution.merge(reason, 1, Integer::sum);
        }
        return sortByValueDesc(distribution);
    }

    /**
     * 生成诊断建议。
     */
    private List<String> buildRecommendations(DagDiagnosisReport report) {
        List<String> recommendations = new ArrayList<>();
        // 关键逻辑：存在重试热点时建议优先优化该节点稳定性。
        if (StringUtils.hasText(report.getRetryHotspotNodeId())) {
            recommendations.add("优先检查节点 " + report.getRetryHotspotNodeId() + " 的输入依赖与执行幂等性，降低重试频次。");
        }
        // 关键逻辑：存在背压热点时建议扩容邮箱或下游消费能力。
        if (StringUtils.hasText(report.getBackpressureHotspotNodeId())) {
            recommendations.add("优先评估节点 " + report.getBackpressureHotspotNodeId() + " 的邮箱容量与消费并发，缓解背压堆积。");
        }
        // 关键逻辑：存在最长等待节点时建议优化依赖拆分与超时阈值。
        if (StringUtils.hasText(report.getLongestWaitNodeId())) {
            recommendations.add("检查节点 " + report.getLongestWaitNodeId() + " 的依赖主题生产时序，必要时调整等待超时与重试退避。");
        }
        // 关键逻辑：未命中热点时给出通用治理建议，确保报告始终可读。
        if (recommendations.isEmpty()) {
            recommendations.add("当前运行未发现明显热点，建议持续观测关键节点耗时与背压指标。");
        }
        return recommendations;
    }

    /**
     * 归一化失败原因码。
     */
    private String normalizeFailureReasonCode(String reasonCode) {
        // 关键逻辑：空原因统一归并 UNKNOWN，保证分布统计完整。
        if (!StringUtils.hasText(reasonCode)) {
            return DagFailureReasonCode.UNKNOWN.name();
        }
        String normalized = reasonCode.trim().toUpperCase();
        try {
            // 关键逻辑：命中枚举时输出标准值，统一统计口径。
            return DagFailureReasonCode.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            log.debug("未知失败原因码, reasonCode={}", reasonCode, exception);
            return DagFailureReasonCode.UNKNOWN.name();
        }
    }

    /**
     * 归一化阻塞原因码。
     */
    private String normalizeBlockingReasonCode(String reasonCode) {
        // 关键逻辑：空原因统一归并 UNKNOWN，保证分布统计完整。
        if (!StringUtils.hasText(reasonCode)) {
            return DagBlockingReasonCode.UNKNOWN.name();
        }
        String normalized = reasonCode.trim().toUpperCase().replace('-', '_');
        if (normalized.contains("MAILBOX")) {
            return DagBlockingReasonCode.MAILBOX_OVERLOADED.name();
        }
        if (normalized.contains("READY_QUEUE_FULL")) {
            return DagBlockingReasonCode.READY_QUEUE_FULL.name();
        }
        if (normalized.contains("READY_QUEUE_THROTTLED") || normalized.contains("READY_QUEUE_BACKOFF")) {
            return DagBlockingReasonCode.READY_QUEUE_THROTTLED.name();
        }
        if (normalized.contains("DEPENDENCY")) {
            return DagBlockingReasonCode.DEPENDENCY_PENDING.name();
        }
        return DagBlockingReasonCode.UNKNOWN.name();
    }

    /**
     * 按值倒序输出 map。
     */
    private Map<String, Integer> sortByValueDesc(Map<String, Integer> source) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    /**
     * 空安全列表。
     */
    private <T> List<T> safeList(List<T> source) {
        if (source == null) {
            return List.of();
        }
        return source;
    }
}

