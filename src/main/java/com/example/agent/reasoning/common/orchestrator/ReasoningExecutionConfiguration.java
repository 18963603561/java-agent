package com.example.agent.reasoning.common.orchestrator;

import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 推理执行线程池配置。
 */
@Configuration
public class ReasoningExecutionConfiguration {

    /**
     * 推理并行执行器。
     *
     * @param reasoningConfigResolver 推理配置解析器
     * @return 执行器
     */
    @Bean(name = "reasoningExecutor")
    public Executor reasoningExecutor(ReasoningConfigResolver reasoningConfigResolver) {
        int poolSize = reasoningConfigResolver.resolveExecutorPoolSize();
        return Executors.newFixedThreadPool(poolSize);
    }
}
