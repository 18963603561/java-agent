package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 反思策略选择器。
 *
 * <p>用途：按配置生成策略执行顺序，避免业务服务中散落策略选择分支。</p>
 */
@Component
public class ReflectionStrategySelector {

    /**
     * 反思配置。
     */
    private final ReflectionProperties reflectionProperties;

    /**
     * 反思稳定性评分器。
     */
    private final ReflectionStabilityScorer stabilityScorer;

    /**
     * 可注入策略列表。
     */
    private final List<ReflectionStrategy> reflectionStrategies;

    @Autowired
    public ReflectionStrategySelector(ReflectionProperties reflectionProperties,
                                      ReflectionStabilityScorer stabilityScorer,
                                      List<ReflectionStrategy> reflectionStrategies) {
        this.reflectionProperties = reflectionProperties;
        this.stabilityScorer = stabilityScorer;
        this.reflectionStrategies = reflectionStrategies == null ? List.of() : List.copyOf(reflectionStrategies);
    }

    /**
     * 兼容构造器，默认不注入稳定性评分器。
     *
     * @param reflectionProperties 反思配置
     * @param reflectionStrategies 策略列表
     */
    public ReflectionStrategySelector(ReflectionProperties reflectionProperties,
                                      List<ReflectionStrategy> reflectionStrategies) {
        // 调用完整构造器，保持默认稳定性评分器为空。
        this(reflectionProperties, null, reflectionStrategies);
    }

    /**
     * 解析可执行策略列表。
     *
     * @return 按执行顺序排列的策略列表
     */
    public List<ReflectionStrategy> resolveOrderedStrategies() {
        // 初始化可执行策略列表。
        List<ReflectionStrategy> enabled = new ArrayList<>();
        // 循环遍历策略列表，筛选可执行策略。
        for (ReflectionStrategy strategy : reflectionStrategies) {
            // 判断策略是否启用，启用时加入列表。
            if (strategy.isEnabled(reflectionProperties)) {
                // 写入可执行策略列表。
                enabled.add(strategy);
            }
        }
        // 对可执行策略按顺序排序。
        enabled.sort(Comparator.comparingInt(ReflectionStrategy::order));
        // 返回可执行策略列表副本。
        return List.copyOf(enabled);
    }

    /**
     * 执行稳定性评估。
     *
     * @param context 反思执行上下文
     * @return 稳定性评估结果
     */
    public ReflectionStabilityEvaluation evaluateStability(ReflectionExecutionContext context) {
        // 判断评分器是否为空，空时返回满分稳定结果。
        if (stabilityScorer == null) {
            // 返回满分稳定评估结果。
            return new ReflectionStabilityEvaluation(1.0D, true, List.of());
        }
        // 调用评分器计算稳定性评分。
        return stabilityScorer.evaluate(context);
    }

    /**
     * 是否允许规则反思兜底。
     *
     * @return true 表示允许
     */
    public boolean allowFallback() {
        // 返回是否允许兜底的配置结果。
        return reflectionProperties.isFallbackEnabled();
    }
}
