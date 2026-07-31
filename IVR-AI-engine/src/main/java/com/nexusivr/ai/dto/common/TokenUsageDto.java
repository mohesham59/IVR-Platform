package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * Cost/usage snapshot for a single LLM call. Mirrors the modelUsed /
 * tokensInput / tokensOutput columns on ai_messages, but is intentionally
 * its own DTO (not a copy of the AiMessage model) because it is also
 * returned by modules that never write to ai_messages at all — Flow
 * Generator, Summarization, and Router all make LLM calls but only
 * Chat's turns are persisted as messages.
 */
public class TokenUsageDto {

    private String modelUsed;
    private Integer tokensInput;
    private Integer tokensOutput;

    public TokenUsageDto() {
    }

    public TokenUsageDto(String modelUsed, Integer tokensInput, Integer tokensOutput) {
        this.modelUsed = modelUsed;
        this.tokensInput = tokensInput;
        this.tokensOutput = tokensOutput;
    }

    /** Convenience total; null if either side is unknown rather than silently treating null as 0. */
    public Integer getTokensTotal() {
        if (tokensInput == null || tokensOutput == null) return null;
        return tokensInput + tokensOutput;
    }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public Integer getTokensInput() { return tokensInput; }
    public void setTokensInput(Integer tokensInput) { this.tokensInput = tokensInput; }

    public Integer getTokensOutput() { return tokensOutput; }
    public void setTokensOutput(Integer tokensOutput) { this.tokensOutput = tokensOutput; }

    @Override
    public String toString() {
        return "TokenUsageDto{" +
                "modelUsed='" + modelUsed + '\'' +
                ", tokensInput=" + tokensInput +
                ", tokensOutput=" + tokensOutput +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TokenUsageDto)) return false;
        TokenUsageDto that = (TokenUsageDto) o;
        return Objects.equals(modelUsed, that.modelUsed) &&
                Objects.equals(tokensInput, that.tokensInput) &&
                Objects.equals(tokensOutput, that.tokensOutput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelUsed, tokensInput, tokensOutput);
    }
}
