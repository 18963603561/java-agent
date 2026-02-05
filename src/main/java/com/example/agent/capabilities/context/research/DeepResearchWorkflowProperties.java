package com.example.agent.capabilities.context.research;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 深度研究流程配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.deep-research")
public class DeepResearchWorkflowProperties {

    private List<WorkflowStep> steps = new ArrayList<>();

    public List<WorkflowStep> getSteps() {
        return steps;
    }

    public void setSteps(List<WorkflowStep> steps) {
        this.steps = steps;
    }

    /**
     * 研究流程步骤定义。
     */
    public static class WorkflowStep {

        private String name;
        private String modelId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }
    }
}
