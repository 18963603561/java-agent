package com.example.agent.contracts;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 接口规范文档导出接口，提供运行时访问入口。
 */
@RestController
public class OpenApiController {

    /**
     * 文档缓存存储。
     */
    private final OpenApiDocumentStore documentStore;

    /**
     * 构造控制器。
     *
     * @param documentStore 文档缓存存储
     */
    public OpenApiController(OpenApiDocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    /**
     * 获取接口规范文档内容。
     *
     * @return 文档内容文本
     */
    @GetMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getOpenApi() {
        return Mono.just(documentStore.getJson());
    }
}
