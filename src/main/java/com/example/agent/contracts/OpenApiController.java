package com.example.agent.contracts;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * OpenAPI 文档导出接口，提供运行时文档访问。
 */
@RestController
public class OpenApiController {

    private final OpenApiDocumentStore documentStore;

    public OpenApiController(OpenApiDocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    /**
     * 获取 OpenAPI 文档。
     *
     * @return OpenAPI JSON 文档
     */
    @GetMapping(path = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getOpenApi() {
        return Mono.just(documentStore.getJson());
    }
}
