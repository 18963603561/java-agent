package com.example.agent.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动日志输出，包含版本、端口与环境摘要。
 */
@Component
public class StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment environment;
    private final BuildProperties buildProperties;

    /**
     * 构造方法，注入运行环境与构建信息。
     *
     * @param environment 运行环境
     * @param buildPropertiesProvider 构建信息提供器
     */
    public StartupLogger(Environment environment, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.environment = environment;
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String appName = environment.getProperty("spring.application.name", "agent-server");
        String port = environment.getProperty("server.port", "8080");
        String[] profiles = environment.getActiveProfiles();
        String profileSummary = profiles.length == 0 ? "default" : String.join(",", profiles);
        String version = buildProperties == null ? "unknown" : buildProperties.getVersion();
        log.info("服务启动完成 应用={}, 版本={}, 端口={}, 环境={}", appName, version, port, profileSummary);
    }
}
