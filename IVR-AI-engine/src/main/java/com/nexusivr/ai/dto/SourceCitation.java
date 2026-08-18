package com.nexusivr.ai.dto;

import java.util.Objects;

/**
 * Data Transfer Object representing a structured RAG source citation.
 */
public class SourceCitation {

    private String sourceName;
    private String relPath;
    private String fileType;
    private String sectionOrPage;
    private double score;
    private String uniqueDocId;
    private String chunkId;
    private String chunkContent;

    public SourceCitation() {
    }

    public SourceCitation(String sourceName, String relPath, String fileType, String sectionOrPage, double score, String uniqueDocId, String chunkId, String chunkContent) {
        this.sourceName = sourceName;
        this.relPath = relPath;
        this.fileType = fileType;
        this.sectionOrPage = sectionOrPage;
        this.score = score;
        this.uniqueDocId = uniqueDocId;
        this.chunkId = chunkId;
        this.chunkContent = chunkContent;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getRelPath() {
        return relPath;
    }

    public void setRelPath(String relPath) {
        this.relPath = relPath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getSectionOrPage() {
        return sectionOrPage;
    }

    public void setSectionOrPage(String sectionOrPage) {
        this.sectionOrPage = sectionOrPage;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getUniqueDocId() {
        return uniqueDocId;
    }

    public void setUniqueDocId(String uniqueDocId) {
        this.uniqueDocId = uniqueDocId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getChunkContent() {
        return chunkContent;
    }

    public void setChunkContent(String chunkContent) {
        this.chunkContent = chunkContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceCitation citation = (SourceCitation) o;
        return Double.compare(citation.score, score) == 0 &&
                Objects.equals(sourceName, citation.sourceName) &&
                Objects.equals(relPath, citation.relPath) &&
                Objects.equals(fileType, citation.fileType) &&
                Objects.equals(sectionOrPage, citation.sectionOrPage) &&
                Objects.equals(uniqueDocId, citation.uniqueDocId) &&
                Objects.equals(chunkId, citation.chunkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceName, relPath, fileType, sectionOrPage, score, uniqueDocId, chunkId);
    }

    @Override
    public String toString() {
        return "SourceCitation{" +
                "sourceName='" + sourceName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", sectionOrPage='" + sectionOrPage + '\'' +
                ", score=" + score +
                ", uniqueDocId='" + uniqueDocId + '\'' +
                '}';
    }
}
