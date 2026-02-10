package com.example.agent.reflection;

import com.example.agent.reflection.prompt.ReflectionPromptProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 反思配置一致性校验器。
 *
 * <p>用途：在启动期执行跨字段一致性校验，阻止非法配置进入运行态。</p>
 */
@Component
public class ReflectionPropertiesValidator {

    /**
     * 反思配置。
     */
    private final ReflectionProperties reflectionProperties;

    /**
     * 反思提示词提供器。
     */
    private final ReflectionPromptProvider reflectionPromptProvider;

    public ReflectionPropertiesValidator(ReflectionProperties reflectionProperties,
                                         ReflectionPromptProvider reflectionPromptProvider) {
        this.reflectionProperties = reflectionProperties;
        this.reflectionPromptProvider = reflectionPromptProvider;
    }

    /**
     * 启动期配置校验。
     */
    @PostConstruct
    public void validate() {
        if (!reflectionProperties.isLlmEnabled() && !reflectionProperties.isFallbackEnabled()) {
            throw new IllegalStateException("reflection_strategy_all_disabled");
        }
        if (reflectionProperties.isLlmEnabled() && reflectionProperties.isPromptStrict()) {
            reflectionPromptProvider.validateTemplateExists();
        }
    }
}

