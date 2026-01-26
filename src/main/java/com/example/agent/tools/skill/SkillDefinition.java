package com.example.agent.tools.skill;

import java.util.List;
import java.util.Map;

/**
 * 技能定义，描述技能的版本与路由。
 */
public class SkillDefinition {

    private String name;
    private SkillVersion version;
    private Map<String, Object> schema;
    private List<SkillRoute> routes;

    public SkillDefinition() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SkillVersion getVersion() {
        return version;
    }

    public void setVersion(SkillVersion version) {
        this.version = version;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    public void setSchema(Map<String, Object> schema) {
        this.schema = schema;
    }

    public List<SkillRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<SkillRoute> routes) {
        this.routes = routes;
    }
}
