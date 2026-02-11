package com.example.agent.orchestration.multiagent.config;

import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

/**
 * MultiAgent 传输装配校验配置。
 * <p>用途：在启动阶段校验 transport 与实际装配一致，避免生产环境误用默认内存实现。</p>
 */
@Configuration
public class MultiAgentTransportConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentTransportConfiguration.class);

    public MultiAgentTransportConfiguration(@Value("${agent.dag.distributed.transport:}") String configuredTransport,
                                            DagMailboxTransport dagMailboxTransport) {
        String transportName = StringUtils.hasText(configuredTransport)
                ? configuredTransport.trim().toLowerCase()
                : "";
        if (!StringUtils.hasText(transportName)) {
            throw new IllegalStateException("agent.dag.distributed.transport 未配置，禁止使用隐式默认传输实现");
        }

        String className = dagMailboxTransport == null ? "" : dagMailboxTransport.getClass().getSimpleName();
        // 关键逻辑：对 inprocess 以外模式执行强校验，防止意外装配内存传输。
        if (!"inprocess".equals(transportName)
                && StringUtils.hasText(className)
                && className.contains("InProcess")) {
            throw new IllegalStateException("transport=" + transportName + " 时不允许装配 InProcess 传输: " + className);
        }
        log.info("MultiAgent 传输装配校验通过, transport={}, bean={}", transportName, className);
    }
}
