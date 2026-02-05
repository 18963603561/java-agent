package com.example.agent.capabilities.tools.skill;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 技能配置项。
 */
@Component
@ConfigurationProperties(prefix = "agent.skills")
public class SkillProperties {

    private List<SkillDefinition> definitions = new ArrayList<>();

    public List<SkillDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<SkillDefinition> definitions) {
        this.definitions = definitions;
    }
}
