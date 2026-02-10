package com.example.agent.reasoning.thoughttree;

import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningStrategy;
import com.example.agent.reasoning.common.ReasoningInput;
import com.example.agent.reasoning.common.config.ReasoningConfigValidator;
import com.example.agent.reasoning.common.result.ThoughtTreePayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 思维树服务，提供分支扩展、评分与最佳路径选择能力。
 */
@Service
public class ThoughtTreeService implements ReasoningStrategy {

    private static final Logger log = LoggerFactory.getLogger(ThoughtTreeService.class);
    private final ReasoningConfigValidator reasoningConfigValidator;
    private final ThoughtTreeScoringPolicy thoughtTreeScoringPolicy;
    private final ThoughtTreeTerminalPolicy thoughtTreeTerminalPolicy;

    public ThoughtTreeService(ReasoningConfigValidator reasoningConfigValidator,
                              ThoughtTreeScoringPolicy thoughtTreeScoringPolicy,
                              ThoughtTreeTerminalPolicy thoughtTreeTerminalPolicy) {
        this.reasoningConfigValidator = reasoningConfigValidator;
        this.thoughtTreeScoringPolicy = thoughtTreeScoringPolicy;
        this.thoughtTreeTerminalPolicy = thoughtTreeTerminalPolicy;
    }

    /**
     * 扩展思维树，返回扁平节点列表以兼容轻量调用方。
     *
     * @param prompt 输入提示
     * @return 扁平节点列表
     */
    public List<ThoughtNode> expand(String prompt) {
        ThoughtTreeResult result = buildTree(prompt, new ThoughtTreeConfig());
        if (result.getRoot() == null) {
            return List.of();
        }
        return flattenTree(result.getRoot());
    }

    /**
     * 构建思维树并返回结构化结果。
     *
     * @param prompt 输入提示
     * @param config 思维树配置
     * @return 思维树结果
     */
    public ThoughtTreeResult buildTree(String prompt, ThoughtTreeConfig config) {
        ThoughtTreeConfig safe = normalizeConfig(config);
        String normalizedPrompt = prompt == null ? "" : prompt.trim();

        ThoughtNode root = new ThoughtNode();
        root.setNodeId("root");
        root.setContent(normalizedPrompt);
        root.setScore(1.0);
        root.setDepth(0);
        root.setExplanation("初始问题");
        root.setChildren(new ArrayList<>());

        ThoughtTreeResult result = new ThoughtTreeResult();
        result.setRoot(root);
        result.setBestPath(new ArrayList<>());

        int expandedNodes = 0;
        int totalTokens = 0;
        Deque<ThoughtNode> queue = new ArrayDeque<>();
        queue.add(root);
        List<ThoughtNode> allNodes = new ArrayList<>();
        allNodes.add(root);
        Map<String, ThoughtNode> nodeIndex = new HashMap<>();
        nodeIndex.put(root.getNodeId(), root);

        log.info("思维树开始, promptLength={}, maxDepth={}, branchingFactor={}",
                normalizedPrompt.length(), safe.getMaxDepth(), safe.getBranchingFactor());

        while (!queue.isEmpty() && expandedNodes < safe.getExplorationBudget()) {
            ThoughtNode current = queue.poll();
            if (current == null) {
                continue;
            }
            if (current.getDepth() >= safe.getMaxDepth()) {
                current.setTerminal(true);
                continue;
            }

            int remainingBudget = safe.getExplorationBudget() - expandedNodes;
            if (remainingBudget <= 0) {
                break;
            }
            int nextBranchLimit = Math.min(safe.getBranchingFactor(), remainingBudget);
            List<String> branches = generateBranches(normalizedPrompt, current, nextBranchLimit);
            for (int i = 0; i < branches.size(); i++) {
                if (expandedNodes >= safe.getExplorationBudget()) {
                    break;
                }
                String content = branches.get(i);
                ThoughtNode node = new ThoughtNode();
                node.setNodeId(current.getNodeId() + "-" + (current.getChildren().size() + 1));
                node.setParentId(current.getNodeId());
                node.setDepth(current.getDepth() + 1);
                node.setContent(content);
                node.setTokensUsed(content.length());
                node.setExplanation("分支扩展");
                node.setChildren(new ArrayList<>());
                node.setScore(evaluateThought(node, safe.getEvaluationMethod()));

                if (node.getScore() < safe.getPruningThreshold()) {
                    continue;
                }

                if (isTerminalThought(content)) {
                    node.setTerminal(true);
                }

                current.getChildren().add(node);
                allNodes.add(node);
                nodeIndex.put(node.getNodeId(), node);
                totalTokens += node.getTokensUsed();
                expandedNodes++;

                if (!node.isTerminal()) {
                    queue.add(node);
                }
            }
            result.setTreeDepth(Math.max(result.getTreeDepth(), current.getDepth() + 1));
        }

        result.setTotalThoughts(expandedNodes);
        result.setTotalTokens(totalTokens);
        result.setBestPath(findBestPath(root));
        if (result.getBestPath() != null && !result.getBestPath().isEmpty()) {
            result.setBestSolution(synthesizeSolution(result.getBestPath(), normalizedPrompt));
            result.setConfidence(calculatePathConfidence(result.getBestPath()));
        } else {
            result.setBestSolution("未找到有效路径");
            result.setConfidence(0.0);
        }

        if (safe.isBacktrackEnabled() && result.getConfidence() < 0.5) {
            ThoughtNode alternative = findBestLeaf(allNodes);
            if (alternative != null) {
                List<ThoughtNode> altPath = buildPath(alternative, nodeIndex);
                double altConfidence = calculatePathConfidence(altPath);
                if (altConfidence > result.getConfidence()) {
                    result.setBestPath(altPath);
                    result.setBestSolution(synthesizeSolution(altPath, normalizedPrompt));
                    result.setConfidence(altConfidence);
                }
            }
        }

        log.info("思维树完成, totalThoughts={}, treeDepth={}, confidence={}",
                result.getTotalThoughts(), result.getTreeDepth(), result.getConfidence());
        return result;
    }

    @Override
    public boolean supports(String strategyType) {
        return StringUtils.hasText(strategyType) && "THOUGHT_TREE".equalsIgnoreCase(strategyType);
    }

    @Override
    public ReasoningResult execute(ReasoningRequest request) {
        ThoughtTreeConfig config = resolveConfig(request != null ? request.getInput() : new ReasoningInput(Map.of()));
        ThoughtTreeResult result = buildTree(request != null ? request.getPrompt() : null, config);
        ThoughtTreePayload payload = new ThoughtTreePayload(
                result.getTotalThoughts(),
                result.getTreeDepth(),
                result.getBestPath(),
                result.getBestSolution()
        );
        return new ReasoningResult(
                "THOUGHT_TREE",
                result.getBestSolution(),
                result.getConfidence(),
                "completed",
                "COMPLETED",
                null,
                payload
        );
    }

    private ThoughtTreeConfig resolveConfig(ReasoningInput input) {
        if (input == null) {
            return new ThoughtTreeConfig();
        }
        ThoughtTreeConfig config = input.getThoughtTreeConfig();
        if (config != null) {
            return config;
        }
        return new ThoughtTreeConfig();
    }

    /**
     * 规整配置，避免非法参数导致推理树构建异常。
     */
    private ThoughtTreeConfig normalizeConfig(ThoughtTreeConfig config) {
        return reasoningConfigValidator.normalizeThoughtTreeConfig(config);
    }

    /**
     * 生成分支思路，当前实现采用规则化模板以保持可预测性。
     */
    private List<String> generateBranches(String prompt, ThoughtNode parent, int branchingFactor) {
        List<String> branches = new ArrayList<>();
        String key = extractKeyPhrase(prompt);
        branches.add("明确目标与约束: " + key);
        branches.add("拆分步骤并确定依赖: " + key);
        branches.add("选择可用工具并执行: " + key);
        branches.add("校验结果与风险点: " + key);

        List<String> trimmed = new ArrayList<>();
        for (String branch : branches) {
            if (branch.length() > 10) {
                trimmed.add(branch);
            }
        }

        List<String> output = new ArrayList<>();
        for (int i = 0; i < trimmed.size() && output.size() < branchingFactor; i++) {
            output.add(trimmed.get(i));
        }

        int index = 1;
        while (output.size() < branchingFactor) {
            output.add("补充思路 " + index + ": " + key);
            index++;
        }
        return output;
    }

    /**
     * 提取输入中的关键短语，用于构造分支提示。
     */
    private String extractKeyPhrase(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "当前任务";
        }
        String[] parts = prompt.split("[，,。.;!?\\s]+");
        if (parts.length == 0) {
            return prompt.trim();
        }
        String candidate = parts[0].trim();
        return candidate.isBlank() ? prompt.trim() : candidate;
    }

    /**
     * 使用可解释规则打分，避免引入额外随机性。
     */
    private double evaluateThought(ThoughtNode node, String method) {
        return thoughtTreeScoringPolicy.evaluate(node, method);
    }

    private boolean isTerminalThought(String thought) {
        return thoughtTreeTerminalPolicy.isTerminal(thought);
    }

    /**
     * 通过 DFS 递归计算真实根到叶路径，避免共享路径栈导致路径失真。
     */
    private List<ThoughtNode> findBestPath(ThoughtNode root) {
        if (root == null) {
            return List.of();
        }
        PathCandidate candidate = findBestPathRecursively(root, new ArrayList<>());
        return candidate.path;
    }

    private PathCandidate findBestPathRecursively(ThoughtNode node, List<ThoughtNode> currentPath) {
        if (node == null) {
            return new PathCandidate(List.of(), 0);
        }
        List<ThoughtNode> nextPath = new ArrayList<>(currentPath);
        nextPath.add(node);

        if (node.getChildren() == null || node.getChildren().isEmpty() || node.isTerminal()) {
            return new PathCandidate(nextPath, averageScore(nextPath));
        }

        PathCandidate best = new PathCandidate(nextPath, averageScore(nextPath));
        for (ThoughtNode child : node.getChildren()) {
            PathCandidate candidate = findBestPathRecursively(child, nextPath);
            if (candidate.score > best.score) {
                best = candidate;
            }
        }
        return best;
    }

    private double averageScore(List<ThoughtNode> path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (ThoughtNode node : path) {
            sum += node.getScore();
        }
        return sum / path.size();
    }

    private ThoughtNode findBestLeaf(List<ThoughtNode> nodes) {
        ThoughtNode best = null;
        double bestScore = 0;
        for (ThoughtNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (node.getChildren() == null || node.getChildren().isEmpty() || node.isTerminal()) {
                if (node.getScore() > bestScore) {
                    bestScore = node.getScore();
                    best = node;
                }
            }
        }
        return best;
    }

    private List<ThoughtNode> buildPath(ThoughtNode target, Map<String, ThoughtNode> nodeIndex) {
        List<ThoughtNode> path = new ArrayList<>();
        ThoughtNode current = target;
        while (current != null && current.getNodeId() != null) {
            path.add(0, current);
            String parentId = current.getParentId();
            if (parentId == null) {
                break;
            }
            current = nodeIndex.get(parentId);
        }
        return path;
    }

    private String synthesizeSolution(List<ThoughtNode> path, String prompt) {
        if (path == null || path.isEmpty()) {
            return "未找到有效路径";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("问题: ").append(prompt).append('\n');
        builder.append("路径:").append('\n');
        int index = 1;
        for (ThoughtNode node : path) {
            if ("root".equals(node.getNodeId())) {
                continue;
            }
            builder.append(index).append(". ").append(node.getContent()).append('\n');
            index++;
        }
        return builder.toString().trim();
    }

    private double calculatePathConfidence(List<ThoughtNode> path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        double avg = averageScore(path);
        double depthPenalty = 1.0 / (1.0 + path.size() * 0.1);
        return avg * depthPenalty;
    }

    private List<ThoughtNode> flattenTree(ThoughtNode root) {
        List<ThoughtNode> nodes = new ArrayList<>();
        Deque<ThoughtNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ThoughtNode node = stack.pop();
            nodes.add(node);
            if (node.getChildren() != null) {
                for (ThoughtNode child : node.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return nodes;
    }

    /**
     * 路径候选对象，封装路径及其评分。
     */
    private record PathCandidate(List<ThoughtNode> path, double score) {
    }
}
