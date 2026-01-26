package com.example.agent.contracts;

/**
 * OpenAPI 文档缓存对象，提供运行时文档访问入口。
 */
public class OpenApiDocumentStore {

    private final String json;

    public OpenApiDocumentStore(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }
}
