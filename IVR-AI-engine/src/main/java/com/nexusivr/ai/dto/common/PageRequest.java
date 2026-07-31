package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * Generic pagination + sorting input, reused by every request DTO that
 * lists rows (Conversation history lookups, Analytics drill-downs, etc.)
 * instead of each feature inventing its own page/size fields.
 *
 * sortDirection is a plain String ("ASC"/"DESC") rather than an enum on
 * purpose: it is a thin, universally-understood convention, and adding a
 * one-off SortDirection enum for two literal values would be more
 * ceremony than value at the DTO layer.
 */
public class PageRequest {

    private int page;
    private int size;
    private String sortBy;
    private String sortDirection;

    public PageRequest() {
        this.page = 0;
        this.size = 20;
        this.sortDirection = "DESC";
    }

    public PageRequest(int page, int size, String sortBy, String sortDirection) {
        this.page = page;
        this.size = size;
        this.sortBy = sortBy;
        this.sortDirection = sortDirection;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

    @Override
    public String toString() {
        return "PageRequest{" +
                "page=" + page +
                ", size=" + size +
                ", sortBy='" + sortBy + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageRequest)) return false;
        PageRequest that = (PageRequest) o;
        return page == that.page && size == that.size &&
                Objects.equals(sortBy, that.sortBy) &&
                Objects.equals(sortDirection, that.sortDirection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page, size, sortBy, sortDirection);
    }
}
