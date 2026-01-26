package com.example.agent.model;

/**
 * 模型请求。
 */
public class ModelRequest {

    private String prompt;
    private ModelScene scene;

    public ModelRequest() {
    }

    public ModelRequest(String prompt, ModelScene scene) {
        this.prompt = prompt;
        this.scene = scene;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public ModelScene getScene() {
        return scene;
    }

    public void setScene(ModelScene scene) {
        this.scene = scene;
    }
}
