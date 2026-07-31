package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * A node in the Internal Flow Model.
 * Each node has a type, optional content (prompt, menu, etc.), and metadata.
 */
public class FlowNode {
    private String id;
    private FlowNodeType type;
    private String title;
    private String subtitle;
    private FlowPrompt prompt;
    private FlowMenu menu;
    private FlowInput input;
    private FlowTransfer transfer;
    private FlowQueue queue;
    private FlowCondition condition;
    private FlowBusinessHours businessHours;
    private FlowRecording recording;
    private FlowApi api;
    private FlowDatabase database;
    private FlowVoicemail voicemail;
    private FlowWebhook webhook;
    private FlowAi ai;
    private String voicexmlRef; // reference to original VoiceXML form id

    public FlowNode() {
    }

    public FlowNode(String id, FlowNodeType type, String title) {
        this.id = id;
        this.type = type;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public FlowNodeType getType() {
        return type;
    }

    public void setType(FlowNodeType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public FlowPrompt getPrompt() {
        return prompt;
    }

    public void setPrompt(FlowPrompt prompt) {
        this.prompt = prompt;
    }

    public FlowMenu getMenu() {
        return menu;
    }

    public void setMenu(FlowMenu menu) {
        this.menu = menu;
    }

    public FlowInput getInput() {
        return input;
    }

    public void setInput(FlowInput input) {
        this.input = input;
    }

    public FlowTransfer getTransfer() {
        return transfer;
    }

    public void setTransfer(FlowTransfer transfer) {
        this.transfer = transfer;
    }

    public FlowQueue getQueue() {
        return queue;
    }

    public void setQueue(FlowQueue queue) {
        this.queue = queue;
    }

    public FlowCondition getCondition() {
        return condition;
    }

    public void setCondition(FlowCondition condition) {
        this.condition = condition;
    }

    public FlowBusinessHours getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(FlowBusinessHours businessHours) {
        this.businessHours = businessHours;
    }

    public FlowRecording getRecording() {
        return recording;
    }

    public void setRecording(FlowRecording recording) {
        this.recording = recording;
    }

    public FlowApi getApi() {
        return api;
    }

    public void setApi(FlowApi api) {
        this.api = api;
    }

    public FlowDatabase getDatabase() {
        return database;
    }

    public void setDatabase(FlowDatabase database) {
        this.database = database;
    }

    public FlowVoicemail getVoicemail() {
        return voicemail;
    }

    public void setVoicemail(FlowVoicemail voicemail) {
        this.voicemail = voicemail;
    }

    public FlowWebhook getWebhook() {
        return webhook;
    }

    public void setWebhook(FlowWebhook webhook) {
        this.webhook = webhook;
    }

    public FlowAi getAi() {
        return ai;
    }

    public void setAi(FlowAi ai) {
        this.ai = ai;
    }

    public String getVoicexmlRef() {
        return voicexmlRef;
    }

    public void setVoicexmlRef(String voicexmlRef) {
        this.voicexmlRef = voicexmlRef;
    }

    public boolean hasContent() {
        return prompt != null || menu != null || input != null || transfer != null ||
               queue != null || condition != null || businessHours != null || recording != null ||
               api != null || database != null || voicemail != null || webhook != null || ai != null;
    }

    @Override
    public String toString() {
        return "FlowNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", title='" + title + '\'' +
                '}';
    }
}
