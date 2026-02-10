package com.example.agent.reflection.prompt;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 反思提示词模板渲染器。
 *
 * <p>用途：执行模板占位符替换，输出最终可发送给模型的提示词文本。</p>
 */
@Component
public class ReflectionPromptTemplateEngine {

    /**
     * 渲染模板。
     *
     * @param template 模板文本
     * @param variables 变量映射
     * @return 渲染结果
     */
    public String render(String template, Map<String, String> variables) {
        if (template == null) {
            throw new IllegalStateException("reflection_prompt_template_empty");
        }
        String rendered = template;
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() == null ? "" : entry.getValue();
                rendered = rendered.replace("{{" + key + "}}", value);
            }
        }
        return rendered;
    }
}

