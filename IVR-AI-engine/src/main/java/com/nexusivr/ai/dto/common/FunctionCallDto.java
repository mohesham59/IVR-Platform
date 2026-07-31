package com.nexusivr.ai.dto.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One concrete function invocation chosen by the model at runtime — the
 * "filled-in" counterpart to FunctionDefinitionDto. callId lets a caller
 * correlate this invocation with the eventual tool-execution result when
 * it is fed back on a later turn (that feedback loop is out of scope for
 * these DTOs, which only cover the request/response shapes, not services).
 */
public class FunctionCallDto {

    private String callId;
    private String name;
    private Map<String, Object> arguments;

    public FunctionCallDto() {
        this.arguments = new HashMap<>();
    }

    public FunctionCallDto(String callId, String name, Map<String, Object> arguments) {
        this.callId = callId;
        this.name = name;
        this.arguments = arguments != null ? arguments : new HashMap<>();
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

    @Override
    public String toString() {
        return "FunctionCallDto{" +
                "callId='" + callId + '\'' +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionCallDto)) return false;
        FunctionCallDto that = (FunctionCallDto) o;
        return Objects.equals(callId, that.callId) && Objects.equals(name, that.name) &&
                Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId, name, arguments);
    }
}
