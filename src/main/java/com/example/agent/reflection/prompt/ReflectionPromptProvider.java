package com.example.agent.reflection.prompt;

import com.example.agent.reflection.ReflectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 反思提示词提供器。
 *
 * <p>用途：基于配置版本加载并缓存反思模板，渲染上下文后返回最终提示词。</p>
 */
@Component
public class ReflectionPromptProvider {

    private static final Logger log = LoggerFactory.getLogger(ReflectionPromptProvider.class);

    private static final String TEMPLATE_PATH_PATTERN = "classpath:prompts/reflection/review-%s.prompt";

    /**
     * 反思配置。
     */
    private final ReflectionProperties reflectionProperties;

    /**
     * 资源加载器。
     */
    private final ResourceLoader resourceLoader;

    /**
     * 对象序列化器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 模板渲染器。
     */
    private final ReflectionPromptTemplateEngine templateEngine;

    /**
     * 模板缓存。
     */
    private final AtomicReference<TemplateCache> templateCache = new AtomicReference<>();

    public ReflectionPromptProvider(ReflectionProperties reflectionProperties,
                                    ResourceLoader resourceLoader,
                                    ObjectMapper objectMapper,
                                    ReflectionPromptTemplateEngine templateEngine) {
        this.reflectionProperties = reflectionProperties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
    }

    /**
     * 构建反思提示词。
     *
     * @param context 反思上下文对象
     * @return 提示词文本
     */
    public String buildPrompt(Object context) {
        String contextJson = serializeContext(context);
        String template = loadTemplate(reflectionProperties.getPromptVersion());
        return templateEngine.render(template, Map.of("contextJson", contextJson));
    }

    /**
     * 检查配置模板是否存在。
     */
    public void validateTemplateExists() {
        String version = reflectionProperties.getPromptVersion();
        String templatePath = resolveTemplatePath(version);
        Resource resource = resourceLoader.getResource(templatePath);
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("reflection_prompt_template_missing:" + version);
        }
    }

    private String serializeContext(Object context) {
        try {
            return objectMapper.writeValueAsString(context == null ? Map.of() : context);
        } catch (Exception ex) {
            log.warn("反思提示词上下文序列化失败, reason={}", ex.getMessage(), ex);
            return "{}";
        }
    }

    private String loadTemplate(String version) {
        TemplateCache cache = templateCache.get();
        if (cache != null && cache.version.equals(version)) {
            log.debug("反思模板缓存命中, version={}", version);
            return cache.template;
        }

        String templatePath = resolveTemplatePath(version);
        Resource resource = resourceLoader.getResource(templatePath);
        if (resource == null || !resource.exists()) {
            if (reflectionProperties.isPromptStrict()) {
                throw new IllegalStateException("reflection_prompt_template_missing:" + version);
            }
            log.warn("反思模板缺失，使用内置兜底模板, version={}, templatePath={}", version, templatePath);
            String fallbackTemplate = defaultFallbackTemplate();
            templateCache.set(new TemplateCache(version, fallbackTemplate));
            return fallbackTemplate;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String loaded = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            templateCache.set(new TemplateCache(version, loaded));
            log.info("反思模板缓存已初始化, version={}, templatePath={}, templateLength={}",
                    version,
                    templatePath,
                    loaded.length());
            return loaded;
        } catch (IOException ex) {
            if (reflectionProperties.isPromptStrict()) {
                throw new IllegalStateException("reflection_prompt_template_read_failed:" + version, ex);
            }
            log.warn("反思模板读取失败，使用内置兜底模板, version={}, reason={}", version, ex.getMessage(), ex);
            String fallbackTemplate = defaultFallbackTemplate();
            templateCache.set(new TemplateCache(version, fallbackTemplate));
            return fallbackTemplate;
        }
    }

    private String resolveTemplatePath(String version) {
        return TEMPLATE_PATH_PATTERN.formatted(version == null || version.isBlank() ? "v1" : version);
    }

    private String defaultFallbackTemplate() {
        return "你是步骤执行的质量审查员。\n"
                + "你的任务是基于上下文评估步骤质量并判断是否重试。\n\n"
                + "输出必须是单个 JSON 对象，不允许任何额外文本。\n"
                + "字段约束：score(0~1)、retry(boolean)、notes(string)。\n"
                + "最小示例：{\"score\":0.5,\"retry\":false,\"notes\":\"\"}\n\n"
                + "REFLECTION_CONTEXT_JSON:\n{{contextJson}}";
    }

    private record TemplateCache(String version, String template) {
    }
}
