package com.example.agent.reasoning.thoughttree;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 思维树终止判定策略。
 *
 * <p>用途：统一管理终止节点判定规则，降低思维树服务中的规则耦合。
 */
@Component
public class ThoughtTreeTerminalPolicy {

    /**
     * 判定是否终止节点。
     *
     * @param thought 思维文本
     * @return 是否终止
     */
    public boolean isTerminal(String thought) {
        if (thought == null) {
            return false;
        }
        String content = thought.toLowerCase(Locale.ROOT);
        return content.contains("最终") || content.contains("结论") || content.contains("答案")
                || content.contains("无法") || content.contains("无解");
    }
}

