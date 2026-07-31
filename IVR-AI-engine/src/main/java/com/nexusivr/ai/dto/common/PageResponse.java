package com.nexusivr.ai.dto.common;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Generic pagination envelope for any list of results (Conversation
 * history entries, Analytics breakdown rows, etc.). Kept generic (<T>)
 * rather than duplicated per-feature so every paginated response looks
 * identical to API consumers.
 */
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public PageResponse() {
        this.content = new ArrayList<>();
    }

    public PageResponse(List<T> content, int page, int size, long totalElements,
                         int totalPages, boolean hasNext, boolean hasPrevious) {
        this.content = content != null ? content : new ArrayList<>();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }

    public boolean isHasPrevious() { return hasPrevious; }
    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }

    @Override
    public String toString() {
        return "PageResponse{" +
                "content=" + content +
                ", page=" + page +
                ", size=" + size +
                ", totalElements=" + totalElements +
                ", totalPages=" + totalPages +
                ", hasNext=" + hasNext +
                ", hasPrevious=" + hasPrevious +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageResponse)) return false;
        PageResponse<?> that = (PageResponse<?>) o;
        return page == that.page && size == that.size &&
                totalElements == that.totalElements && totalPages == that.totalPages &&
                hasNext == that.hasNext && hasPrevious == that.hasPrevious &&
                Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }
}
