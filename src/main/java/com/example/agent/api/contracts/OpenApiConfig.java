package com.example.agent.api.contracts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 接口规范文档加载配置，负责在启动时读取契约文件并缓存。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(OpenApiConfig.class);

    /**
     * 文档序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 契约文件路径。
     */
    @Value("${agent.openapi.contract-path:specs/001-agent-core-spec/contracts/openapi.yaml}")
    private String contractPath;

    /**
     * 构造加载配置。
     *
     * @param objectMapper 文档序列化工具
     */
    public OpenApiConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载并缓存接口规范文档，启动后由控制器提供访问。
     *
     * @return 文档缓存对象
     */
    @Bean
    public OpenApiDocumentStore openApiDocumentStore() {
        Path path = Path.of(contractPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("OpenAPI contract file not found: " + path.toAbsolutePath());
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object document = new Yaml().load(inputStream);
            String json = objectMapper.writeValueAsString(document);
            log.info("OpenAPI contract loaded, path={}", path.toAbsolutePath());
            return new OpenApiDocumentStore(json);
        } catch (IOException ex) {
            log.error("OpenAPI contract load failed, path={}", path.toAbsolutePath(), ex);
            throw new IllegalStateException("OpenAPI contract load failed", ex);
        }
    }
}
