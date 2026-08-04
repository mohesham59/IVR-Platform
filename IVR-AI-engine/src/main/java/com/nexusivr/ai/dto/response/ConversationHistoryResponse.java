package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.ConversationHistoryEntryDto;
import com.nexusivr.ai.dto.common.PageResponse;

import java.util.Objects;

/**
 * Output of the Conversation module's history lookup. Thin wrapper
 * around PageResponse<ConversationHistoryEntryDto> rather than returning
 * the PageResponse directly, so this module's return type stays a named,
 * stable class in the API (easier to version independently later) rather
 * than a raw generic instantiation appearing directly on the controller
 * signature.
 */
public class ConversationHistoryResponse {

    private PageResponse<ConversationHistoryEntryDto> entries;

    public ConversationHistoryResponse() {
        this.entries = new PageResponse<>();
    }

    public ConversationHistoryResponse(PageResponse<ConversationHistoryEntryDto> entries) {
        this.entries = entries != null ? entries : new PageResponse<>();
    }

    public PageResponse<ConversationHistoryEntryDto> getEntries() { return entries; }
    public void setEntries(PageResponse<ConversationHistoryEntryDto> entries) { this.entries = entries; }

    @Override
    public String toString() {
        return "ConversationHistoryResponse{" +
                "entries=" + entries +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationHistoryResponse)) return false;
        ConversationHistoryResponse that = (ConversationHistoryResponse) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }
}
