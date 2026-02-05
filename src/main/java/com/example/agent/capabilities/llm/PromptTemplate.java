package com.example.agent.capabilities.llm;

import com.example.agent.capabilities.context.ContextSnapshot;
import java.util.List;

/**
 * 提示词模板接口。
 */
public interface PromptTemplate {

    /**
     * 基于最小渲染上下文生成提示词消息。
     *
     * @param context 渲染上下文
     * @return 消息列表
     */
    List<PromptMessage> render(PromptRenderContext context);

    /**
     * 兼容旧接口：将快照映射为最小渲染上下文后再渲染。
     *
     * @param snapshot 上下文快照
     * @return 消息列表
     */
    default List<PromptMessage> render(ContextSnapshot snapshot) {
        return render(PromptRenderContext.fromSnapshot(snapshot));
    }

    /**
     * 获取模板标识。
     *
     * @return 模板标识
     */
    String getTemplateId();
}
