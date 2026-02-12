package com.example.agent.capabilities.context.compression.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 压缩配置校验结果。
 */
public class CompressionConfigValidationResult {

    /**
     * 是否通过校验。
     */
    private boolean valid = true;

    /**
     * 错误列表。
     */
    private final List<String> errors = new ArrayList<>();

    /**
     * 告警列表。
     */
    private final List<String> warnings = new ArrayList<>();

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public void addError(String error) {
        if (error == null || error.isBlank()) {
            return;
        }
        errors.add(error);
        valid = false;
    }

    public void addWarning(String warning) {
        if (warning == null || warning.isBlank()) {
            return;
        }
        warnings.add(warning);
    }
}

