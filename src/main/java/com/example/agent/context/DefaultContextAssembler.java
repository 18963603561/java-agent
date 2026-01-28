package com.example.agent.context;

import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextCompressionResult;
import com.example.agent.budget.ContextPruneResult;
import com.example.agent.budget.ContextTrimReport;
import com.example.agent.memory.TokenEstimator;
import com.example.agent.model.PromptMessage;
import com.example.agent.model.PromptRole;
import com.example.agent.model.PromptTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认上下文装配器，用于生成提示词装配输入。
 */
@Service
public class DefaultContextAssembler implements ContextAssembler {

    private final PromptTemplate promptTemplate;
    private final TokenEstimator tokenEstimator;

    public DefaultContextAssembler(PromptTemplate promptTemplate, TokenEstimator tokenEstimator) {
        this.promptTemplate = promptTemplate;
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public PromptAssemblyInput assemble(ContextSnapshot snapshot,
                                        ContextBudgetAllocation allocation,
                                        ContextTrimReport trimReport,
                                        ContextPruneResult pruneResult,
                                        ContextCompressionResult compressionResult,
                                        String tenantId,
                                        String workflowId,
                                        String userText) {
        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setTenantId(tenantId);
        input.setWorkflowId(workflowId);
        input.setBudgetAllocation(allocation);
        if (allocation != null && StringUtils.hasText(allocation.getVersion())) {
            input.setPolicyVersion(allocation.getVersion());
        }
        fillSystemDeveloper(input, snapshot);
        input.setUserText(resolveUserText(snapshot, userText));
        input.setTruncatedSections(new ArrayList<>());
        fillBudgetUsage(input);
        return input;
    }

    private void fillSystemDeveloper(PromptAssemblyInput input, ContextSnapshot snapshot) {
        if (promptTemplate == null) {
            return;
        }
        List<PromptMessage> messages = promptTemplate.render(snapshot);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (PromptMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if (message.getRole() == PromptRole.SYSTEM) {
                input.setSystemText(message.getContent());
            } else if (message.getRole() == PromptRole.DEVELOPER) {
                input.setDeveloperText(message.getContent());
            }
        }
    }

    private String resolveUserText(ContextSnapshot snapshot, String userText) {
        if (StringUtils.hasText(userText)) {
            return userText;
        }
        if (snapshot != null && snapshot.getTaskIntent() != null
                && StringUtils.hasText(snapshot.getTaskIntent().getInputText())) {
            return snapshot.getTaskIntent().getInputText();
        }
        return null;
    }

    private void fillBudgetUsage(PromptAssemblyInput input) {
        Map<String, Integer> tokens = new HashMap<>();
        Map<String, Integer> chars = new HashMap<>();
        int systemTokens = estimateTokens(input.getSystemText());
        int developerTokens = estimateTokens(input.getDeveloperText());
        int userTokens = estimateTokens(input.getUserText());
        int totalTokens = systemTokens + developerTokens + userTokens;
        tokens.put("system", systemTokens);
        tokens.put("developer", developerTokens);
        tokens.put("user", userTokens);
        tokens.put("total", totalTokens);

        int systemChars = length(input.getSystemText());
        int developerChars = length(input.getDeveloperText());
        int userChars = length(input.getUserText());
        int totalChars = systemChars + developerChars + userChars;
        chars.put("system", systemChars);
        chars.put("developer", developerChars);
        chars.put("user", userChars);
        chars.put("total", totalChars);

        input.setBudgetUsedTokens(tokens);
        input.setBudgetUsedChars(chars);
    }

    private int estimateTokens(String text) {
        if (tokenEstimator == null) {
            return text == null ? 0 : Math.max(1, (int) Math.ceil(text.length() / 4.0));
        }
        return tokenEstimator.estimateTokens(text);
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }
}
