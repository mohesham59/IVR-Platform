package com.nexusivr.ai.ai;

import com.nexusivr.ai.dto.common.ProviderAttemptDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standardized data carrier for AI provider generation results.
 */
public class AiResponse {

    private final String content;
    private final String model;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final boolean mock;
    private final boolean templateFallback;
    private final String functionName;
    private final String functionArguments;
    private final int statusCode;
    private final String selectedProvider;
    private final String actualProviderUsed;
    private final List<ProviderAttemptDto> providerAttempts;

    public AiResponse(String content, String model, int promptTokens, int completionTokens, boolean mock) {
        this(content, model, promptTokens, completionTokens, mock, false, null, null, 0, null, null, null);
    }

    public AiResponse(String content, String model, int promptTokens, int completionTokens, boolean mock, String functionName, String functionArguments) {
        this(content, model, promptTokens, completionTokens, mock, false, functionName, functionArguments, 0, null, null, null);
    }

    public AiResponse(String content, String model, int promptTokens, int completionTokens, boolean mock, String functionName, String functionArguments, int statusCode) {
        this(content, model, promptTokens, completionTokens, mock, false, functionName, functionArguments, statusCode, null, null, null);
    }

    public AiResponse(String content, String model, int promptTokens, int completionTokens, boolean mock, boolean templateFallback, String functionName, String functionArguments, int statusCode) {
        this(content, model, promptTokens, completionTokens, mock, templateFallback, functionName, functionArguments, statusCode, null, null, null);
    }

    public AiResponse(String content, String model, int promptTokens, int completionTokens, boolean mock, boolean templateFallback, String functionName, String functionArguments, int statusCode, String selectedProvider, String actualProviderUsed) {
        this(content, model, promptTokens, completionTokens, mock, templateFallback, functionName, functionArguments, statusCode, selectedProvider, actualProviderUsed, null);
    }

    public AiResponse(String content, String model, int promptTokens, int completionTokens, boolean mock, boolean templateFallback, String functionName, String functionArguments, int statusCode, String selectedProvider, String actualProviderUsed, List<ProviderAttemptDto> providerAttempts) {
        this.content = content != null ? content : "";
        this.model = model != null ? model : "unknown";
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.totalTokens = this.promptTokens + this.completionTokens;
        this.mock = mock;
        this.templateFallback = templateFallback;
        this.functionName = functionName;
        this.functionArguments = functionArguments;
        this.statusCode = statusCode;
        this.selectedProvider = selectedProvider != null ? selectedProvider : "";
        this.actualProviderUsed = actualProviderUsed != null ? actualProviderUsed : "";
        this.providerAttempts = providerAttempts != null ? new ArrayList<>(providerAttempts) : new ArrayList<>();
    }

    public static AiResponse mockResponse(String content, String model) {
        int inputTokens = content != null ? Math.max(1, content.length() / 4) : 0;
        int outputTokens = content != null ? Math.max(1, content.length() / 4) : 0;
        return new AiResponse(content, model, inputTokens, outputTokens, true);
    }

    public List<ProviderAttemptDto> getProviderAttempts() {
        return providerAttempts;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public boolean isMock() {
        return mock;
    }

    public boolean isTemplateFallback() {
        return templateFallback;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String getFunctionArguments() {
        return functionArguments;
    }

    public boolean hasFunctionCall() {
        return functionName != null && !functionName.isBlank();
    }

    public String getSelectedProvider() {
        return selectedProvider;
    }

    public String getActualProviderUsed() {
        return actualProviderUsed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiResponse aiResponse)) return false;
        return promptTokens == aiResponse.promptTokens &&
                completionTokens == aiResponse.completionTokens &&
                totalTokens == aiResponse.totalTokens &&
                mock == aiResponse.mock &&
                templateFallback == aiResponse.templateFallback &&
                statusCode == aiResponse.statusCode &&
                Objects.equals(content, aiResponse.content) &&
                Objects.equals(model, aiResponse.model) &&
                Objects.equals(functionName, aiResponse.functionName) &&
                Objects.equals(functionArguments, aiResponse.functionArguments) &&
                Objects.equals(selectedProvider, aiResponse.selectedProvider) &&
                Objects.equals(actualProviderUsed, aiResponse.actualProviderUsed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, model, promptTokens, completionTokens, totalTokens, mock, templateFallback, functionName, functionArguments, statusCode, selectedProvider, actualProviderUsed);
    }

    @Override
    public String toString() {
        return "AiResponse{" +
                "content='" + content + '\'' +
                ", model='" + model + '\'' +
                ", promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens +
                ", mock=" + mock +
                ", templateFallback=" + templateFallback +
                ", statusCode=" + statusCode +
                ", functionName='" + functionName + '\'' +
                ", selectedProvider='" + selectedProvider + '\'' +
                ", actualProviderUsed='" + actualProviderUsed + '\'' +
                '}';
    }
}
