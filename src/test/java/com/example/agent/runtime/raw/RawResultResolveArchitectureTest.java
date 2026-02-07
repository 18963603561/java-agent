package com.example.agent.runtime.raw;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 原始结果解析服务架构约束测试。
 *
 * <p>用途：防止 RawResultResolveService 回退到直接依赖具体存储实现类。
 */
class RawResultResolveArchitectureTest {

    @Test
    void resolveServiceShouldNotDependOnConcreteStores() throws Exception {
        Path file = Path.of("src/main/java/com/example/agent/runtime/raw/RawResultResolveService.java");
        String source = Files.readString(file, StandardCharsets.UTF_8);

        assertFalse(source.contains("InMemoryRawResultStore"), "不允许依赖 InMemoryRawResultStore");
        assertFalse(source.contains("RedisRawResultStore"), "不允许依赖 RedisRawResultStore");
        assertFalse(source.contains("SizeAwareRawResultStore"), "不允许依赖 SizeAwareRawResultStore");
        assertFalse(source.contains("resolveLegacyRawKey"), "不允许保留 legacy raw key 分支");
    }
}

