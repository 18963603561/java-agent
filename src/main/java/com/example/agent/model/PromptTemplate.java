package com.example.agent.model;

import com.example.agent.context.ContextSnapshot;
import java.util.List;

/**
 * 提示词模板接口。
 */
public interface PromptTemplate {

    /**
     * 基于上下文渲染系统与开发者消息。
     *
     * @param snapshot 上下文快照
     * @return 消息列表
     */
    List<PromptMessage> render(ContextSnapshot snapshot);

    /**
     * 获取模板标识。
     *
     * @return 模板标识
     */
    String getTemplateId();
}