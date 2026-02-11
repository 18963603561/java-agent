package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DAG 分布式 Actor 配置。
 *
 * <p>用途：统一承载跨进程消息、分片、租约与重试等运行参数。</p>
 */
@Component
@ConfigurationProperties(prefix = "agent.dag.distributed")
public class DagDistributedProperties {

    private static final Logger log = LoggerFactory.getLogger(DagDistributedProperties.class);

    /**
     * 当前实例标识。
     */
    private String instanceId = "instance-local";

    /**
     * 单次拉取消息批大小。
     */
    private int pollBatchSize = 128;

    /**
     * 当前可用实例列表。
     */
    private List<String> activeInstances = new ArrayList<>(List.of("instance-local"));

    /**
     * 消息重投递最大次数。
     */
    private int maxDeliveryAttempts = 6;

    /**
     * 消息重投递最小退避毫秒。
     */
    private long nackBackoffMinMs = 100L;

    /**
     * 消息重投递最大退避毫秒。
     */
    private long nackBackoffMaxMs = 5_000L;

    /**
     * 节点租约时长毫秒。
     */
    private long leaseTtlMs = 30_000L;

    /**
     * 去重键生存时间毫秒。
     */
    private long dedupTtlMs = 3_600_000L;

    @PostConstruct
    public void validateOnStartup() {
        List<String> normalizedInstances = normalizeActiveInstances(activeInstances);
        if (normalizedInstances.isEmpty()) {
            log.error("DAG分布式配置非法, activeInstances为空, instanceId={}", instanceId);
            throw new IllegalStateException("agent.dag.distributed.active-instances 不能为空");
        }
        if (!StringUtils.hasText(instanceId)) {
            log.error("DAG分布式配置非法, instanceId为空, activeInstances={}", normalizedInstances);
            throw new IllegalStateException("agent.dag.distributed.instance-id 不能为空");
        }
        if (!normalizedInstances.contains(instanceId)) {
            log.warn("DAG实例配置不一致, instanceId未包含在activeInstances中, instanceId={}, activeInstances={}",
                    instanceId,
                    normalizedInstances);
        }
        this.activeInstances = new ArrayList<>(normalizedInstances);
        log.info("DAG分布式配置加载完成, instanceId={}, activeInstances={}", instanceId, this.activeInstances);
    }

    public List<String> resolveActiveInstancesOrThrow(String scene) {
        List<String> normalizedInstances = normalizeActiveInstances(activeInstances);
        if (normalizedInstances.isEmpty()) {
            log.error("DAG分片路由配置非法, scene={}, activeInstances={}", scene, activeInstances);
            throw new IllegalStateException("agent.dag.distributed.active-instances 不能为空");
        }
        if (!normalizedInstances.equals(activeInstances)) {
            log.warn("DAG分片路由实例列表已归一化, scene={}, before={}, after={}",
                    scene,
                    activeInstances,
                    normalizedInstances);
            this.activeInstances = new ArrayList<>(normalizedInstances);
        }
        if (log.isDebugEnabled()) {
            log.debug("DAG分片路由实例列表, scene={}, activeInstances={}", scene, normalizedInstances);
        }
        return normalizedInstances;
    }

    private List<String> normalizeActiveInstances(List<String> instances) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String instance : instances) {
            if (!StringUtils.hasText(instance)) {
                continue;
            }
            String trimmed = instance.trim();
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getPollBatchSize() {
        return pollBatchSize;
    }

    public void setPollBatchSize(int pollBatchSize) {
        this.pollBatchSize = pollBatchSize;
    }

    public List<String> getActiveInstances() {
        return activeInstances;
    }

    public void setActiveInstances(List<String> activeInstances) {
        this.activeInstances = activeInstances;
    }

    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }

    public void setMaxDeliveryAttempts(int maxDeliveryAttempts) {
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    public long getNackBackoffMinMs() {
        return nackBackoffMinMs;
    }

    public void setNackBackoffMinMs(long nackBackoffMinMs) {
        this.nackBackoffMinMs = nackBackoffMinMs;
    }

    public long getNackBackoffMaxMs() {
        return nackBackoffMaxMs;
    }

    public void setNackBackoffMaxMs(long nackBackoffMaxMs) {
        this.nackBackoffMaxMs = nackBackoffMaxMs;
    }

    public long getLeaseTtlMs() {
        return leaseTtlMs;
    }

    public void setLeaseTtlMs(long leaseTtlMs) {
        this.leaseTtlMs = leaseTtlMs;
    }

    public long getDedupTtlMs() {
        return dedupTtlMs;
    }

    public void setDedupTtlMs(long dedupTtlMs) {
        this.dedupTtlMs = dedupTtlMs;
    }
}
