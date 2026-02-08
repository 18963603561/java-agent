package com.example.agent.planning;

import com.example.agent.api.http.dto.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 规划提示词构建器。
 *
 * <p>用途：集中负责规划提示词模板加载与渲染，避免在服务中维护超长内嵌字符串。
 */
@Component
public class PlanningPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlanningPromptBuilder.class);

    private static final String DEFAULT_TEMPLATE_PATH =
            "classpath:prompts/planning/planner-plan-prompt.md";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String templatePath;

    public PlanningPromptBuilder(ObjectMapper objectMapper,
                                 ResourceLoader resourceLoader,
                                 @Value("${agent.planner.prompt-template-path:" + DEFAULT_TEMPLATE_PATH + "}")
                                 String templatePath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.templatePath = templatePath;
    }

    /**
     * 构建规划提示词。
     *
     * @param request 任务请求
     * @param contextSummary 上下文摘要
     * @return 规划提示词文本
     */
    public String buildPrompt(TaskRequest request, Map<String, Object> contextSummary) {
        String contextJson = serializePromptContext(request, contextSummary);
        String template = loadTemplate();
        return template.formatted(contextJson);
    }

    private String serializePromptContext(TaskRequest request, Map<String, Object> contextSummary) {
        Map<String, Object> promptContext = new HashMap<>();
        promptContext.put("query", request != null ? request.getQuery() : null);
        promptContext.put("contextSummary", contextSummary == null ? Map.of() : contextSummary);
        try {
            return objectMapper.writeValueAsString(promptContext);
        } catch (Exception ex) {
            log.warn("规划提示词上下文序列化失败, reason={}", ex.getMessage(), ex);
            return "{}";
        }
    }

    private String loadTemplate() {
        Resource resource = resourceLoader.getResource(templatePath);
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("planner_prompt_template_missing");
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("planner_prompt_template_read_failed", ex);
        }
    }
}

