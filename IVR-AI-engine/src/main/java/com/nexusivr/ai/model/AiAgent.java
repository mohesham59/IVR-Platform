package com.nexusivr.ai.model;

import java.util.List;

/**
 * Represents an AI Agent capable of specific behavioral instructions and prompt suggestions.
 */
public class AiAgent {
    private final String id;
    private final String name;
    private final String description;
    private final String systemPrompt;
    private final List<String> suggestions;

    public AiAgent(String id, String name, String description, String systemPrompt, List<String> suggestions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.suggestions = suggestions;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSystemPrompt() { return systemPrompt; }
    public List<String> getSuggestions() { return suggestions; }
}
