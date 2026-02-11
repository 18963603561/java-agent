package com.example.agent.orchestration.multiagent.config;

import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxTransport;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagAuditRepository;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagDeadLetterRepository;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagMessageDedupRepository;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagRuntimeStateRepository;
import com.example.agent.orchestration.multiagent.handoff.HandoffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * MultiAgent 存储装配校验配置。
 * <p>用途：在生产环境下禁止 InMemory 存储实现被误装配，确保分布式语义一致。</p>
 */
@Configuration
public class MultiAgentStorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentStorageConfiguration.class);

    /**
     * 生产环境存储实现校验。
     * <p>关键逻辑：当配置声明为 persistent 时，启动阶段强制校验为非 InMemory 实现。</p>
     */
    @Configuration
    @Profile("prod")
    @ConditionalOnProperty(prefix = "agent.multiagent.storage", name = "mode", havingValue = "persistent")
    static class PersistentStorageGuard {

        PersistentStorageGuard(HandoffRepository handoffRepository,
                               DagRuntimeStateRepository runtimeStateRepository,
                               DagMessageDedupRepository dedupRepository,
                               DagDeadLetterRepository deadLetterRepository,
                               DagAuditRepository dagAuditRepository,
                               DagMailboxTransport mailboxTransport) {
            assertNotInMemory("HandoffRepository", handoffRepository);
            assertNotInMemory("DagRuntimeStateRepository", runtimeStateRepository);
            assertNotInMemory("DagMessageDedupRepository", dedupRepository);
            assertNotInMemory("DagDeadLetterRepository", deadLetterRepository);
            assertNotInMemory("DagAuditRepository", dagAuditRepository);
            assertNotInMemory("DagMailboxTransport", mailboxTransport);
            log.info("MultiAgent 存储装配校验通过, 已启用 persistent 模式");
        }

        private void assertNotInMemory(String beanName, Object bean) {
            if (bean == null) {
                throw new IllegalStateException(beanName + " 未装配");
            }
            String className = bean.getClass().getName();
            if (StringUtils.hasText(className) && className.contains("InMemory")) {
                throw new IllegalStateException(beanName + " 不允许使用 InMemory 实现: " + className);
            }
        }
    }
}
