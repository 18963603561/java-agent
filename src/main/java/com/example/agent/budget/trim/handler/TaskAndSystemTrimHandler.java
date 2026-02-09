package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.TaskIntent;
import java.util.List;

/**
 * 任务与系统分组裁剪处理器。
 */
public class TaskAndSystemTrimHandler extends AbstractContextTrimHandler {

    @Override
    public ContextTrimSection section() {
        return ContextTrimSection.TASK_AND_SYSTEM;
    }

    @Override
    public void trimBySectionBudget(TrimContext context) {
        Integer systemBudget = resolveBudget(context.getBudgets(), ContextSection.SYSTEM_POLICY);
        int systemTokens = context.getCurrentTokens().getOrDefault(ContextSection.SYSTEM_POLICY, 0);
        if (systemBudget != null && systemTokens > systemBudget) {
            trimRoleBoundarySystem(context, systemBudget);
            context.refreshTokens();
        }
        Integer developerBudget = resolveBudget(context.getBudgets(), ContextSection.DEVELOPER_POLICY);
        int developerTokens = context.getCurrentTokens().getOrDefault(ContextSection.DEVELOPER_POLICY, 0);
        if (developerBudget != null && developerTokens > developerBudget) {
            trimRoleBoundaryDeveloper(context, developerBudget);
            context.refreshTokens();
        }
        Integer taskBudget = resolveBudget(context.getBudgets(), ContextSection.USER_INPUT);
        int taskTokens = context.getCurrentTokens().getOrDefault(ContextSection.USER_INPUT, 0);
        if (taskBudget != null && taskTokens > taskBudget) {
            trimTaskIntent(context, taskBudget);
            context.refreshTokens();
        }
    }

    @Override
    public int trimByTotalBudget(TrimContext context, int excess) {
        if (excess <= 0) {
            return 0;
        }
        int systemTokens = context.getCurrentTokens().getOrDefault(ContextSection.SYSTEM_POLICY, 0);
        if (systemTokens > 0 && excess > 0) {
            int target = Math.max(0, systemTokens - excess);
            int after = trimRoleBoundarySystem(context, target);
            context.refreshTokens();
            excess -= Math.max(0, systemTokens - after);
        }
        int developerTokens = context.getCurrentTokens().getOrDefault(ContextSection.DEVELOPER_POLICY, 0);
        if (developerTokens > 0 && excess > 0) {
            int target = Math.max(0, developerTokens - excess);
            int after = trimRoleBoundaryDeveloper(context, target);
            context.refreshTokens();
            excess -= Math.max(0, developerTokens - after);
        }
        int taskTokens = context.getCurrentTokens().getOrDefault(ContextSection.USER_INPUT, 0);
        if (taskTokens > 0 && excess > 0) {
            int target = Math.max(0, taskTokens - excess);
            int after = trimTaskIntent(context, target);
            context.refreshTokens();
            excess -= Math.max(0, taskTokens - after);
        }
        return Math.max(0, excess);
    }

    private int trimTaskIntent(TrimContext context,
                               int targetTokens) {
        TaskIntent intent = context.getSnapshot().getTaskIntent();
        if (intent == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        int currentTokens = estimator.estimateTaskIntentTokens(intent);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<String> constraints = mutableCopy(intent.getConstraints());
        if (constraints != null) {
            while (!constraints.isEmpty() && currentTokens > targetTokens) {
                String removed = constraints.remove(constraints.size() - 1);
                recordRemoved(context, ContextSection.USER_INPUT, 1,
                        estimator.safeLength(removed), estimator.estimateTokens(removed));
                currentTokens = estimator.estimateTaskIntentTokens(intentWith(intent, constraints));
            }
            intent.setConstraints(constraints.isEmpty() ? null : constraints);
        }
        currentTokens = trimTextField(context,
                intent.getRequiredOutput(),
                targetTokens,
                currentTokens,
                intent::setRequiredOutput,
                ContextSection.USER_INPUT);
        currentTokens = trimTextField(context,
                intent.getFailurePolicy(),
                targetTokens,
                currentTokens,
                intent::setFailurePolicy,
                ContextSection.USER_INPUT);
        currentTokens = trimTextField(context,
                intent.getSuccessCriteria(),
                targetTokens,
                currentTokens,
                intent::setSuccessCriteria,
                ContextSection.USER_INPUT);
        trimTextField(context,
                intent.getInputText(),
                targetTokens,
                currentTokens,
                intent::setInputText,
                ContextSection.USER_INPUT);
        return estimator.estimateTaskIntentTokens(intent);
    }

    private TaskIntent intentWith(TaskIntent intent, List<String> constraints) {
        TaskIntent temp = new TaskIntent();
        temp.setInputText(intent.getInputText());
        temp.setSuccessCriteria(intent.getSuccessCriteria());
        temp.setFailurePolicy(intent.getFailurePolicy());
        temp.setRequiredOutput(intent.getRequiredOutput());
        temp.setConstraints(constraints);
        return temp;
    }

    private int trimRoleBoundarySystem(TrimContext context,
                                       int targetTokens) {
        RoleBoundary boundary = context.getSnapshot().getRoleBoundary();
        if (boundary == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        int currentTokens = estimator.estimateRoleBoundarySystemTokens(boundary);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<String> forbiddenActions = mutableCopy(boundary.getForbiddenActions());
        if (forbiddenActions != null) {
            while (!forbiddenActions.isEmpty() && currentTokens > targetTokens) {
                String removed = forbiddenActions.remove(forbiddenActions.size() - 1);
                recordRemoved(context, ContextSection.SYSTEM_POLICY, 1,
                        estimator.safeLength(removed), estimator.estimateTokens(removed));
                boundary.setForbiddenActions(forbiddenActions.isEmpty() ? null : forbiddenActions);
                currentTokens = estimator.estimateRoleBoundarySystemTokens(boundary);
            }
        }
        List<String> dataScopes = mutableCopy(boundary.getDataScopes());
        if (dataScopes != null && currentTokens > targetTokens) {
            while (!dataScopes.isEmpty() && currentTokens > targetTokens) {
                String removed = dataScopes.remove(dataScopes.size() - 1);
                recordRemoved(context, ContextSection.SYSTEM_POLICY, 1,
                        estimator.safeLength(removed), estimator.estimateTokens(removed));
                boundary.setDataScopes(dataScopes.isEmpty() ? null : dataScopes);
                currentTokens = estimator.estimateRoleBoundarySystemTokens(boundary);
            }
        }
        currentTokens = trimTextField(context,
                boundary.getRiskLevel(),
                targetTokens,
                currentTokens,
                boundary::setRiskLevel,
                ContextSection.SYSTEM_POLICY);
        trimTextField(context,
                boundary.getSystemPolicyId(),
                targetTokens,
                currentTokens,
                boundary::setSystemPolicyId,
                ContextSection.SYSTEM_POLICY);
        return estimator.estimateRoleBoundarySystemTokens(boundary);
    }

    private int trimRoleBoundaryDeveloper(TrimContext context,
                                          int targetTokens) {
        RoleBoundary boundary = context.getSnapshot().getRoleBoundary();
        if (boundary == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        int currentTokens = estimator.estimateRoleBoundaryDeveloperTokens(boundary);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        trimTextField(context,
                boundary.getDeveloperPolicyId(),
                targetTokens,
                currentTokens,
                boundary::setDeveloperPolicyId,
                ContextSection.DEVELOPER_POLICY);
        return estimator.estimateRoleBoundaryDeveloperTokens(boundary);
    }

    private int trimTextField(TrimContext context,
                              String value,
                              int targetTokens,
                              int currentTokens,
                              java.util.function.Consumer<String> setter,
                              ContextSection section) {
        if (value == null || currentTokens <= targetTokens) {
            return currentTokens;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        int textTokens = estimator.estimateTokens(value);
        int targetTextTokens = Math.max(0, textTokens - (currentTokens - targetTokens));
        String trimmed = trimTextByTokens(value, targetTextTokens);
        if (trimmed != null && trimmed.length() < value.length()) {
            recordRemoved(context, section, 1,
                    value.length() - trimmed.length(),
                    estimator.estimateTokensByChars(value.length() - trimmed.length()));
            setter.accept(trimmed.isBlank() ? null : trimmed);
            currentTokens = currentTokens - textTokens + estimator.estimateTokens(trimmed);
        }
        return currentTokens;
    }
}
