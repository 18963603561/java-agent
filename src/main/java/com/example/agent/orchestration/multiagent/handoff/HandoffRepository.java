package com.example.agent.orchestration.multiagent.handoff;

import java.util.Optional;

/**
 * 交接记录仓储接口。
 *
 * <p>用途：抽象交接记录生命周期存储，支持幂等查询与版本冲突控制。</p>
 */
public interface HandoffRepository {

    /**
     * 创建交接记录。
     *
     * @param record 待创建记录
     * @return 创建后记录
     */
    HandoffRecord create(HandoffRecord record);

    /**
     * 根据交接标识查询记录。
     *
     * @param handoffId 交接标识
     * @return 查询结果
     */
    Optional<HandoffRecord> findById(String handoffId);

    /**
     * 根据幂等键查询记录。
     *
     * @param idempotencyKey 幂等键
     * @return 查询结果
     */
    Optional<HandoffRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * 基于版本号执行乐观并发更新。
     *
     * @param record 待更新记录
     * @param expectedVersion 期望版本
     * @return 更新后记录
     */
    HandoffRecord compareAndSet(HandoffRecord record, long expectedVersion);
}

