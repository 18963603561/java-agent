package com.example.agent.runtime;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.tools.McpToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工具参数校验器，用于判断直达工具路径是否可执行。
 */
@Component
public class ToolArgumentValidator {

    private static final Logger log = LoggerFactory.getLogger(ToolArgumentValidator.class);

    private final ToolRegistry toolRegistry;

    public ToolArgumentValidator(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 校验工具名称与参数是否满足最小可执行条件。
     *
     * <p>输入：工具名称与参数映射。
     * <p>输出：校验结果。
     */
    public ValidationResult validate(String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(toolName)) {
            return ValidationResult.invalid("tool_name_missing");
        }
        if (arguments == null || arguments.isEmpty()) {
            return ValidationResult.invalid("arguments_missing");
        }
        List<String> missing = resolveMissingRequiredFields(toolName, arguments);
        if (!missing.isEmpty()) {
            return ValidationResult.invalid("required_fields_missing", missing);
        }
        return ValidationResult.valid();
    }

    private List<String> resolveMissingRequiredFields(String toolName, Map<String, Object> arguments) {
        List<String> missing = new ArrayList<>();
        if (toolRegistry == null || !StringUtils.hasText(toolName)) {
            return missing;
        }
        McpToolDefinition definition = toolRegistry.getDefinition(toolName);
        if (definition == null || definition.getInputSchema() == null) {
            return missing;
        }
        Object required = definition.getInputSchema().get("required");
        if (!(required instanceof List<?> requiredList)) {
            return missing;
        }
        for (Object item : requiredList) {
            if (item == null) {
                continue;
            }
            String key = item.toString();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (!arguments.containsKey(key) || arguments.get(key) == null) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            log.debug("工具参数缺失, tool={}, missing={}", toolName, missing);
        }
        return missing;
    }

    /**
     * 校验结果。
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final String reason;
        private final List<String> missingFields;

        private ValidationResult(boolean valid, String reason, List<String> missingFields) {
            this.valid = valid;
            this.reason = reason;
            this.missingFields = missingFields == null ? List.of() : missingFields;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, List.of());
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, List.of());
        }

        public static ValidationResult invalid(String reason, List<String> missingFields) {
            return new ValidationResult(false, reason, missingFields);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }
    }
}
