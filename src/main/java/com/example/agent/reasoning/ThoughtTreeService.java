package com.example.agent.reasoning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 思维树服务，提供分支扩展、评分与最佳路径选择能力。
 */
@Service
public class ThoughtTreeService {

    private static final Logger log = LoggerFactory.getLogger(ThoughtTreeService.class);

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

        int explored = 0;
        int totalTokens = 0;
        Deque<ThoughtNode> queue = new ArrayDeque<>();
        queue.add(root);
        List<ThoughtNode> allNodes = new ArrayList<>();
        allNodes.add(root);

        log.info("思维树开始, promptLength={}, maxDepth={}, branchingFactor={}",
                normalizedPrompt.length(), safe.getMaxDepth(), safe.getBranchingFactor());

        while (!queue.isEmpty() && explored < safe.getExplorationBudget()) {
            ThoughtNode current = queue.poll();
            if (current == null) {
                continue;
            }
            if (current.getDepth() >= safe.getMaxDepth()) {
                current.setTerminal(true);
                continue;
            }

            List<String> branches = generateBranches(normalizedPrompt, current, safe.getBranchingFactor());
            explored += branches.size();
            for (int i = 0; i < branches.size(); i++) {
                String content = branches.get(i);
                ThoughtNode node = new ThoughtNode();
                node.setNodeId(current.getNodeId() + "-" + (i + 1));
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
                totalTokens += node.getTokensUsed();
                if (!node.isTerminal()) {
                    queue.add(node);
                }
            }
            result.setTreeDepth(Math.max(result.getTreeDepth(), current.getDepth() + 1));
        }

        result.setTotalThoughts(explored);
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
                List<ThoughtNode> altPath = buildPath(alternative, allNodes);
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

    private ThoughtTreeConfig normalizeConfig(ThoughtTreeConfig config) {
        ThoughtTreeConfig safe = config == null ? new ThoughtTreeConfig() : config;
        if (safe.getMaxDepth() <= 0) {
            safe.setMaxDepth(3);
        }
        if (safe.getBranchingFactor() <= 0) {
            safe.setBranchingFactor(3);
        }
        if (safe.getBranchingFactor() > 4) {
            safe.setBranchingFactor(4);
        }
        if (safe.getExplorationBudget() <= 0) {
            safe.setExplorationBudget(12);
        }
        if (safe.getPruningThreshold() <= 0) {
            safe.setPruningThreshold(0.3);
        }
        if (safe.getEvaluationMethod() == null || safe.getEvaluationMethod().isBlank()) {
            safe.setEvaluationMethod("scoring");
        }
        return safe;
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

        int idx = 1;
        while (output.size() < branchingFactor) {
            output.add("补充思路 " + idx + ": " + key);
            idx++;
        }
        return output;
    }

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
     * 评分逻辑采用可解释的规则打分，避免引入不稳定的随机性。
     */
    private double evaluateThought(ThoughtNode node, String method) {
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

    private boolean isTerminalThought(String thought) {
        if (thought == null) {
            return false;
        }
        String content = thought.toLowerCase(Locale.ROOT);
        return content.contains("最终") || content.contains("结论") || content.contains("答案")
                || content.contains("无法") || content.contains("无解");
    }

    private List<ThoughtNode> findBestPath(ThoughtNode root) {
        if (root == null) {
            return List.of();
        }
        List<ThoughtNode> bestPath = new ArrayList<>();
        double bestScore = 0;

        Deque<ThoughtNode> path = new ArrayDeque<>();
        Deque<ThoughtNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ThoughtNode node = stack.pop();
            path.push(node);
            if (node.getChildren() == null || node.getChildren().isEmpty() || node.isTerminal()) {
                List<ThoughtNode> current = new ArrayList<>(path);
                double score = averageScore(current);
                if (score > bestScore) {
                    bestScore = score;
                    bestPath = new ArrayList<>(current);
                }
            } else {
                for (ThoughtNode child : node.getChildren()) {
                    stack.push(child);
                }
            }
            path.pop();
        }
        return bestPath;
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

    private List<ThoughtNode> buildPath(ThoughtNode target, List<ThoughtNode> allNodes) {
        List<ThoughtNode> path = new ArrayList<>();
        ThoughtNode current = target;
        Set<String> visited = new HashSet<>();
        while (current != null && current.getNodeId() != null && !visited.contains(current.getNodeId())) {
            path.add(0, current);
            visited.add(current.getNodeId());
            current = findParent(current.getParentId(), allNodes);
        }
        return path;
    }

    private ThoughtNode findParent(String parentId, List<ThoughtNode> allNodes) {
        if (parentId == null) {
            return null;
        }
        for (ThoughtNode node : allNodes) {
            if (parentId.equals(node.getNodeId())) {
                return node;
            }
        }
        return null;
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
}
