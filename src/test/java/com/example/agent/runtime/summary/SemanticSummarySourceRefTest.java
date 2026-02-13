package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义摘要来源引用序列化测试。
 */
class SemanticSummarySourceRefTest {

    @Test
    void shouldSerializeSourceRefsIntoSummaryMap() {
        // 构建摘要基础配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 构建场景化配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建场景解析器。
        SemanticSummaryPolicyResolver policyResolver = new SemanticSummaryPolicyResolver();
        // 构建预算解析器。
        SemanticSummaryBudgetResolver budgetResolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建摘要服务。
        SemanticSummaryService summaryService = new SemanticSummaryService(properties, policyResolver, budgetResolver);
        // 构建摘要构建器。
        StepOutputSummaryBuilder builder = new StepOutputSummaryBuilder(properties, summaryService, null, null, null);

        // 构建结果数据映射。
        Map<String, Object> data = new LinkedHashMap<>();
        // 写入结果数据字段。
        data.put("id", "1");
        // 写入结果数据字段。
        data.put("name", "alpha");
        // 构建结果映射。
        Map<String, Object> result = new LinkedHashMap<>();
        // 写入结果类型字段。
        result.put("kind", "SEARCH");
        // 写入结果数据字段。
        result.put("data", data);
        // 构建输出映射。
        Map<String, Object> output = new LinkedHashMap<>();
        // 写入原始引用字段。
        output.put(RuntimeOutputKeys.RAW_REF, "rawref:1");
        // 写入结果字段。
        output.put(RuntimeOutputKeys.RESULT, result);

        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .output(output)
                .toolName("search_tool")
                .build();
        // 调用摘要构建器生成摘要映射。
        Map<String, Object> summaryMap = builder.build(input);

        // 读取来源引用对象。
        Object refsObj = summaryMap.get(RuntimeOutputKeys.SUMMARY_SOURCE_REFS);
        // 校验来源引用为列表且不为空。
        boolean hasRefs = refsObj instanceof List<?> list && !list.isEmpty();
        // 断言来源引用列表存在。
        assertTrue(hasRefs);

        // 初始化命中标记。
        boolean hasRawRef = false;
        // 初始化命中标记。
        boolean hasToolRef = false;
        // 初始化命中标记。
        boolean hasResultPath = false;
        // 设计意图：遍历引用列表验证关键引用存在。
        // 循环遍历来源引用列表。
        for (Object item : (List<?>) refsObj) {
            // 判断元素是否为映射，非映射跳过。
            if (!(item instanceof Map<?, ?> map)) {
                // 跳过非映射元素，继续处理下一项。
                continue;
            }
            // 读取引用类型字段。
            Object typeObj = map.get(RuntimeOutputKeys.SUMMARY_SOURCE_REF_TYPE);
            // 读取引用值字段。
            Object valueObj = map.get(RuntimeOutputKeys.SUMMARY_SOURCE_REF_VALUE);
            // 读取引用路径字段。
            Object pathObj = map.get(RuntimeOutputKeys.SUMMARY_SOURCE_REF_PATH);
            // 判断是否命中原始引用。
            if ("rawRef".equals(String.valueOf(typeObj)) && "rawref:1".equals(String.valueOf(valueObj))) {
                // 标记原始引用命中。
                hasRawRef = true;
            }
            // 判断是否命中工具引用。
            if ("toolRef".equals(String.valueOf(typeObj)) && "search_tool".equals(String.valueOf(valueObj))) {
                // 标记工具引用命中。
                hasToolRef = true;
            }
            // 判断是否命中结果路径引用。
            if ("resultPath".equals(String.valueOf(typeObj)) && "result.data.id".equals(String.valueOf(pathObj))) {
                // 标记结果路径命中。
                hasResultPath = true;
            }
        }

        // 校验原始引用命中。
        assertTrue(hasRawRef);
        // 校验工具引用命中。
        assertTrue(hasToolRef);
        // 校验结果路径命中。
        assertTrue(hasResultPath);
    }
}
