package com.example.agent.reflection;

import com.example.agent.reflection.prompt.ReflectionPromptProvider;
import com.example.agent.reflection.prompt.ReflectionPromptTemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectionPropertiesValidationTest {

    private ReflectionPropertiesValidator createValidator(ReflectionProperties properties) {
        ReflectionPromptProvider promptProvider = new ReflectionPromptProvider(
                properties,
                new DefaultResourceLoader(),
                new ObjectMapper(),
                new ReflectionPromptTemplateEngine()
        );
        return new ReflectionPropertiesValidator(properties, promptProvider);
    }

    @Test
    void validateThrowsWhenAllStrategiesDisabled() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(false);
        ReflectionPropertiesValidator validator = createValidator(properties);
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void validateThrowsWhenTemplateMissingAndStrict() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        properties.setPromptStrict(true);
        properties.setPromptVersion("v404");
        ReflectionPropertiesValidator validator = createValidator(properties);
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void validatePassesWhenTemplateMissingAndNotStrict() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        properties.setPromptStrict(false);
        properties.setPromptVersion("v404");
        ReflectionPropertiesValidator validator = createValidator(properties);
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void validatePassesWhenLlmDisabledFallbackEnabled() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        ReflectionPropertiesValidator validator = createValidator(properties);
        assertDoesNotThrow(validator::validate);
    }
}

