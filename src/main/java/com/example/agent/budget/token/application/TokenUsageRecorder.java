package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageRecord;

/**
 * 预算记录持久化组件，负责幂等写入语义。
 */
public interface TokenUsageRecorder {

    /**
     * 持久化预算记录。
     *
     * @param record 预算记录
     * @return 是否首次写入成功
     */
    boolean record(TokenUsageRecord record);
}
