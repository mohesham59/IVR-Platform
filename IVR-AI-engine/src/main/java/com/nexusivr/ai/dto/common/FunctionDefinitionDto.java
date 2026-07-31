package com.nexusivr.ai.dto.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes one callable function/tool that the LLM is allowed to invoke
 * for a given turn — the schema side of Function Calling. Passed inbound
 * on ChatRequest and FunctionCallRequest (the caller declares what tools
 * exist); never returned outbound, since responses only ever contain the
 * function the model chose to call (FunctionCallDto), not the catalog it
 * chose from.
 */
public class FunctionDefinitionDto {

    private String name;
    private String description;
    /** JSON-Schema-shaped description of the function's parameters. */
    private Map<String, Object> parametersSchema;

    public FunctionDefinitionDto() {
        this.parametersSchema = new HashMap<>();
    }

    public FunctionDefinitionDto(String name, String description, Map<String, Object> parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema != null ? parametersSchema : new HashMap<>();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getParametersSchema() { return parametersSchema; }
    public void setParametersSchema(Map<String, Object> parametersSchema) { this.parametersSchema = parametersSchema; }

    @Override
    public String toString() {
        return "FunctionDefinitionDto{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", parametersSchema=" + parametersSchema +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionDefinitionDto)) return false;
        FunctionDefinitionDto that = (FunctionDefinitionDto) o;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description) &&
                Objects.equals(parametersSchema, that.parametersSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, parametersSchema);
    }
}
