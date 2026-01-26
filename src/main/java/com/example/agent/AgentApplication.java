package com.example.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口，初始化 Spring Boot 运行环境。
 */
@SpringBootApplication
public class AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplication.class);

    /**
     * 应用主入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        log.info("AgentApplication started");
    }
}
