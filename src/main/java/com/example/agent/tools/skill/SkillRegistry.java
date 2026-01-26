package com.example.agent.tools.skill;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 技能注册表，提供技能配置查询。
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final SkillProperties skillProperties;

    public SkillRegistry(SkillProperties skillProperties) {
        this.skillProperties = skillProperties;
    }

    /**
     * 获取全部技能定义。
     *
     * @return 技能列表
     */
    public List<SkillDefinition> listDefinitions() {
        if (skillProperties.getDefinitions() == null) {
            return Collections.emptyList();
        }
        log.debug("技能注册表加载, count={}", skillProperties.getDefinitions().size());
        return skillProperties.getDefinitions();
    }
}
