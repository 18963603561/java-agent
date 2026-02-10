package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionProperties;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
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
     * 可注入策略列表。
     */
    private final List<ReflectionStrategy> reflectionStrategies;

    public ReflectionStrategySelector(ReflectionProperties reflectionProperties,
                                      List<ReflectionStrategy> reflectionStrategies) {
        this.reflectionProperties = reflectionProperties;
        this.reflectionStrategies = reflectionStrategies == null ? List.of() : List.copyOf(reflectionStrategies);
    }

    /**
     * 解析可执行策略列表。
     *
     * @return 按执行顺序排列的策略列表
     */
    public List<ReflectionStrategy> resolveOrderedStrategies() {
        return reflectionStrategies.stream()
                .filter(strategy -> strategy.isEnabled(reflectionProperties))
                .sorted(Comparator.comparingInt(ReflectionStrategy::order))
                .collect(Collectors.toList());
    }

    /**
     * 是否允许规则反思兜底。
     *
     * @return true 表示允许
     */
    public boolean allowFallback() {
        return reflectionProperties.isFallbackEnabled();
    }
}
