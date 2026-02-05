package com.example.agent.capabilities.tools.hook;

import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于配置阻断工具的 Hook。
 */
@Component
public class BlockedToolHookHandler implements HookHandler {

    /**
     * 默认 Hook 标识。
     */
    public static final String HOOK_ID = "blocked_tool";

    private final HookProperties hookProperties;

    public BlockedToolHookHandler(HookProperties hookProperties) {
        this.hookProperties = hookProperties;
    }

    @Override
    public String getHookId() {
        return HOOK_ID;
    }

    @Override
    public HookDecision handle(HookContext context) {
        if (context == null || context.getHookType() != HookType.PRE_TOOL) {
            return new HookDecision(true, "ignore", Collections.emptyMap());
        }
        if (!hookProperties.isEnabled()) {
            return new HookDecision(true, "disabled", Collections.emptyMap());
        }
        String toolName = context.getToolName();
        if (StringUtils.hasText(toolName) && hookProperties.getBlockedTools().stream()
                .anyMatch(blocked -> blocked.equalsIgnoreCase(toolName))) {
            return new HookDecision(false, "工具被 Hook 阻断", Map.of("toolName", toolName));
        }
        return new HookDecision(true, "ok", Collections.emptyMap());
    }
}
