package com.example.agent;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 启动配置。
 *
 * <p>用途：为 {@code @SpringBootTest} 提供可发现的启动类，避免因生产启动类移包导致测试无法自动定位
 * {@code @SpringBootConfiguration}。</p>
 * <p>边界：仅用于测试范围，不参与生产运行入口。</p>
 */
@SpringBootApplication(scanBasePackages = "com.example.agent")
public class TestSpringBootApplication {
}
