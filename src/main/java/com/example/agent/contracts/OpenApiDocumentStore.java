package com.example.agent.contracts;

/**
 * 接口规范文档缓存对象，提供运行时访问入口。
 */
public class OpenApiDocumentStore {

    /**
     * 文档内容文本。
     */
    private final String json;

    /**
     * 构造文档缓存对象。
     *
     * @param json 文档内容文本
     */
    public OpenApiDocumentStore(String json) {
        this.json = json;
    }

    /**
     * 获取文档内容文本。
     *
     * @return 文档内容文本
     */
    public String getJson() {
        return json;
    }
}
