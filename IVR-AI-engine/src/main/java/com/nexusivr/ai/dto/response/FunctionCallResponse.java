package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.FunctionCallDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Output of the standalone Function Calling module. `functionCalls` is
 * usually non-empty (that's the point of calling this endpoint), but
 * `assistantMessage` can still be populated alongside it when the model
 * wants to say something to the user in the same turn it invokes a tool
 * (e.g. "Sure, let me check that for you" plus a lookup_order call).
 */
public class FunctionCallResponse {

    private List<FunctionCallDto> functionCalls;
    private String assistantMessage;

    public FunctionCallResponse() {
        this.functionCalls = new ArrayList<>();
    }

    public FunctionCallResponse(List<FunctionCallDto> functionCalls, String assistantMessage) {
        this.functionCalls = functionCalls != null ? functionCalls : new ArrayList<>();
        this.assistantMessage = assistantMessage;
    }

    public List<FunctionCallDto> getFunctionCalls() { return functionCalls; }
    public void setFunctionCalls(List<FunctionCallDto> functionCalls) { this.functionCalls = functionCalls; }

    public String getAssistantMessage() { return assistantMessage; }
    public void setAssistantMessage(String assistantMessage) { this.assistantMessage = assistantMessage; }

    @Override
    public String toString() {
        return "FunctionCallResponse{" +
                "functionCalls=" + functionCalls +
                ", assistantMessage='" + assistantMessage + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionCallResponse)) return false;
        FunctionCallResponse that = (FunctionCallResponse) o;
        return Objects.equals(functionCalls, that.functionCalls) && Objects.equals(assistantMessage, that.assistantMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionCalls, assistantMessage);
    }
}
