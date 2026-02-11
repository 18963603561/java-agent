package com.example.agent.orchestration.multiagent.dag.actor;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DAG 节点运行时状态。
 *
 * <p>用途：维护节点依赖计数、状态流转与消息去重信息。</p>
 * <p>边界条件：状态流转必须通过受控方法，禁止外部直接写状态。</p>
 */
public class DagNodeRuntimeState {

    private final String nodeId;
    private final AtomicInteger remainingDependencies;
    private final AtomicReference<DagNodeExecutionStatus> status;
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    public DagNodeRuntimeState(String nodeId, int dependencyCount) {
        this.nodeId = nodeId;
        this.remainingDependencies = new AtomicInteger(Math.max(dependencyCount, 0));
        this.status = new AtomicReference<>(DagNodeExecutionStatus.PENDING);
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getRemainingDependencies() {
        return remainingDependencies.get();
    }

    public DagNodeExecutionStatus getStatus() {
        return status.get();
    }

    /**
     * 注册消息并返回是否首次处理。
     *
     * @param messageId 消息幂等键
     * @return true 表示首次处理，false 表示重复消息
     */
    public boolean registerMessage(String messageId) {
        if (messageId == null) {
            return false;
        }
        return processedMessageIds.add(messageId);
    }

    /**
     * 依赖计数递减。
     *
     * @return 递减后的剩余依赖数
     */
    public int decrementDependency() {
        while (true) {
            int current = remainingDependencies.get();
            if (current == 0) {
                return 0;
            }
            if (remainingDependencies.compareAndSet(current, current - 1)) {
                return current - 1;
            }
        }
    }

    /**
     * 将节点从待处理切换到就绪。
     *
     * @return true 表示切换成功
     */
    public boolean tryReady() {
        if (remainingDependencies.get() > 0) {
            return false;
        }
        return status.compareAndSet(DagNodeExecutionStatus.PENDING, DagNodeExecutionStatus.READY);
    }

    /**
     * 将节点从就绪切换到运行中。
     *
     * @return true 表示切换成功
     */
    public boolean tryStart() {
        return status.compareAndSet(DagNodeExecutionStatus.READY, DagNodeExecutionStatus.RUNNING);
    }

    /**
     * 标记节点执行成功。
     *
     * @return true 表示切换成功
     */
    public boolean trySucceed() {
        return status.compareAndSet(DagNodeExecutionStatus.RUNNING, DagNodeExecutionStatus.SUCCEEDED);
    }

    /**
     * 标记节点执行失败。
     *
     * @return true 表示切换成功
     */
    public boolean tryFail() {
        return status.compareAndSet(DagNodeExecutionStatus.RUNNING, DagNodeExecutionStatus.FAILED);
    }

    /**
     * 强制标记节点失败。
     *
     * <p>用途：用于依赖投递超时、等待超时等非执行线程触发的失败收敛。</p>
     *
     * @return true 表示当前调用触发了状态切换或节点已经是失败态
     */
    public boolean forceFail() {
        while (true) {
            DagNodeExecutionStatus current = status.get();
            if (current == DagNodeExecutionStatus.FAILED) {
                return true;
            }
            if (current == DagNodeExecutionStatus.SUCCEEDED) {
                return false;
            }
            if (status.compareAndSet(current, DagNodeExecutionStatus.FAILED)) {
                return true;
            }
        }
    }

    /**
     * 在失败后重置为就绪状态。
     *
     * <p>用途：支持运行时重试场景下的状态回滚。</p>
     *
     * @return true 表示切换成功
     */
    public boolean tryReadyAfterFailure() {
        if (remainingDependencies.get() > 0) {
            return false;
        }
        return status.compareAndSet(DagNodeExecutionStatus.FAILED, DagNodeExecutionStatus.READY);
    }

    /**
     * 检测节点是否处于终态。
     *
     * <p>说明：在支持失败重试后，仅成功态视为不可变终态。</p>
     */
    public boolean isTerminal() {
        DagNodeExecutionStatus current = status.get();
        return current == DagNodeExecutionStatus.SUCCEEDED;
    }

    /**
     * 强制推进到就绪状态。
     *
     * <p>说明：仅在无依赖节点初始化时使用。</p>
     */
    public void forceReadyWhenNoDependencies() {
        if (remainingDependencies.get() == 0) {
            status.compareAndSet(DagNodeExecutionStatus.PENDING, DagNodeExecutionStatus.READY);
        }
    }

    /**
     * 验证节点标识。
     *
     * @param expectedNodeId 期望节点ID
     */
    public void requireNode(String expectedNodeId) {
        if (!Objects.equals(nodeId, expectedNodeId)) {
            throw new IllegalArgumentException("节点状态不匹配: expected=" + expectedNodeId + ", actual=" + nodeId);
        }
    }
}
