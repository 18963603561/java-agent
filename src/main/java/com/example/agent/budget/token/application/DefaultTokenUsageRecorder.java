package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.repository.TokenUsageRepository;
import org.springframework.stereotype.Component;

/**
 * 默认预算记录持久化组件，委托仓储执行幂等写入。
 */
@Component
public class DefaultTokenUsageRecorder implements TokenUsageRecorder {

    private final TokenUsageRepository repository;

    public DefaultTokenUsageRecorder(TokenUsageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean record(TokenUsageRecord record) {
        return repository.saveIfAbsent(record);
    }
}
