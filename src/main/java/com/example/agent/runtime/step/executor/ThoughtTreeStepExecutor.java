package com.example.agent.runtime.step.executor;

import com.example.agent.reasoning.thoughttree.ThoughtNode;
import com.example.agent.reasoning.thoughttree.ThoughtTreeConfig;
import com.example.agent.reasoning.thoughttree.ThoughtTreeResult;
import com.example.agent.reasoning.thoughttree.ThoughtTreeService;
import com.example.agent.runtime.step.StepExecutionRequest;
import com.example.agent.runtime.step.StepExecutionOutput;
import com.example.agent.streaming.domain.EventType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 思维树步骤执行器。
 *
 * <p>用途：封装 {@code THOUGHT_TREE} 步骤执行逻辑，并发布思维节点展开事件。
 * <p>输入：步骤定义与链路上下文。
 * <p>输出：思维树结果映射。
 * <p>边界：节点为空时不发布事件；异常由上层捕获并按恢复策略处理。
 */
@Component
public class ThoughtTreeStepExecutor implements StepTypeExecutor {

    private final ThoughtTreeService thoughtTreeService;

    public ThoughtTreeStepExecutor(ThoughtTreeService thoughtTreeService) {
        this.thoughtTreeService = thoughtTreeService;
    }

    @Override
    public boolean supports(String stepType) {
        return stepType != null && "THOUGHT_TREE".equalsIgnoreCase(stepType);
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        Map<String, Object> input = resolveStepInput(request.getStep());
        String prompt = input != null && input.get("prompt") instanceof String value ? value : "";
        ThoughtTreeConfig config = new ThoughtTreeConfig();
        ThoughtTreeResult result = thoughtTreeService.buildTree(prompt, config);
        List<ThoughtNode> nodes = flattenThoughtNodes(result.getRoot());
        publishThoughtEvents(request, nodes);

        Map<String, Object> output = new HashMap<>();
        output.put("bestSolution", result.getBestSolution());
        output.put("confidence", result.getConfidence());
        output.put("totalThoughts", result.getTotalThoughts());
        output.put("treeDepth", result.getTreeDepth());
        output.put("nodes", nodes);
        return StepExecutionOutput.fromPayload(output);
    }

    private void publishThoughtEvents(StepExecutionRequest request, List<ThoughtNode> nodes) {
        if (nodes == null || nodes.isEmpty() || request.getEventPublisher() == null) {
            return;
        }
        for (ThoughtNode node : nodes) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("nodeId", node.getNodeId());
            payload.put("score", node.getScore());
            payload.put("depth", node.getDepth());
            if (node.getParentId() != null) {
                payload.put("parentId", node.getParentId());
            }
            request.getEventPublisher().publish(
                    request.getTenantContext(),
                    request.getWorkflowId(),
                    request.getSeqCounter(),
                    EventType.THOUGHT_EXPANDED,
                    payload
            );
        }
    }

    private List<ThoughtNode> flattenThoughtNodes(ThoughtNode root) {
        if (root == null) {
            return List.of();
        }
        List<ThoughtNode> nodes = new java.util.ArrayList<>();
        java.util.ArrayDeque<ThoughtNode> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ThoughtNode node = queue.poll();
            nodes.add(node);
            if (node.getChildren() != null) {
                queue.addAll(node.getChildren());
            }
        }
        return nodes;
    }

    private Map<String, Object> resolveStepInput(com.example.agent.runtime.model.StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }
}
