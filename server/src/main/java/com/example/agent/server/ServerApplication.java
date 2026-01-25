package com.example.agent.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 服务启动入口，负责装配基础配置并启动服务。
 */
@SpringBootApplication
public class ServerApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
