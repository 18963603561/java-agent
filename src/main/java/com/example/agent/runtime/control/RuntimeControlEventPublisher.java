package com.example.agent.runtime.control;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行时控制事件发布器。
 *
 * <p>用途：在执行门禁与审批门禁中发布统一运行时事件，避免门禁组件依赖主编排实现细节。
 * <p>输入：租户上下文、工作流标识、序列计数器、事件类型与事件载荷。
 * <p>输出：无。
 * <p>边界：发布失败由调用方实现自行处理。
 */
@FunctionalInterface
public interface RuntimeControlEventPublisher {

    /**
     * 发布运行时事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序列计数器
     * @param type 事件类型
     * @param payload 事件载荷
     */
    void publish(TenantContext tenantContext,
                 String workflowId,
                 AtomicLong seqCounter,
                 EventType type,
                 Map<String, Object> payload);
}
