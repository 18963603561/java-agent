package com.example.agent.model;

import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.memory.TokenEstimator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 默认提示词组装器。
 */
@Service
public class DefaultPromptAssembler implements PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(DefaultPromptAssembler.class);

    private final PromptTemplate promptTemplate;
    private final TokenEstimator tokenEstimator;

    public DefaultPromptAssembler(PromptTemplate promptTemplate, TokenEstimator tokenEstimator) {
        this.promptTemplate = promptTemplate;
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public PromptBundle build(String prompt, TaskRequest taskRequest, Map<String, Object> stepInput) {
        ContextSnapshot snapshot = resolveSnapshot(taskRequest, stepInput);
        List<PromptMessage> messages = new ArrayList<>();
        if (promptTemplate != null) {
            messages.addAll(promptTemplate.render(snapshot));
        }
        messages.add(new PromptMessage(PromptRole.USER, prompt == null ? "" : prompt));

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        bundle.setEstimatedTokens(estimateTokens(messages));
        bundle.setTruncatedSections(List.of());

        log.debug("提示词组装完成, templateId={}, messageCount={}",
                bundle.getTemplateId(), messages.size());
        return bundle;
    }

    private ContextSnapshot resolveSnapshot(TaskRequest taskRequest, Map<String, Object> stepInput) {
        if (stepInput != null) {
            Object snapshot = stepInput.get("contextSnapshot");
            if (snapshot instanceof ContextSnapshot value) {
                return value;
            }
        }
        if (taskRequest != null && taskRequest.getContext() != null) {
            Object snapshot = taskRequest.getContext().get("contextSnapshot");
            if (snapshot instanceof ContextSnapshot value) {
                return value;
            }
        }
        return null;
    }

    private int estimateTokens(List<PromptMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (PromptMessage message : messages) {
            if (message == null) {
                continue;
            }
            total += tokenEstimator.estimateTokens(message.getContent());
        }
        return total;
    }
}
