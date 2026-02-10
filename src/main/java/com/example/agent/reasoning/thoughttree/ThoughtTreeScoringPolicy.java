package com.example.agent.reasoning.thoughttree;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 思维树评分策略。
 *
 * <p>用途：统一管理思维节点评分规则，降低服务类中的算法耦合度。
 */
@Component
public class ThoughtTreeScoringPolicy {

    /**
     * 评估思维节点得分。
     *
     * @param node 思维节点
     * @param method 评估方法
     * @return 归一化得分
     */
    public double evaluate(ThoughtNode node, String method) {
        String content = node.getContent() == null ? "" : node.getContent().toLowerCase(Locale.ROOT);
        double score = 0.5;

        if (content.contains("结论") || content.contains("因此") || content.contains("最终")) {
            score += 0.2;
        }
        if (content.contains("步骤") || content.contains("拆分") || content.contains("执行")) {
            score += 0.1;
        }
        if (content.contains("可能") || content.contains("也许") || content.contains("猜测")) {
            score -= 0.1;
        }
        if (content.length() < 12) {
            score -= 0.1;
        }
        score -= node.getDepth() * 0.05;

        if (score < 0) {
            score = 0;
        }
        if (score > 1) {
            score = 1;
        }
        return score;
    }
}

