package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * A single sentiment measurement. Reused by both the Sentiment module
 * (as the primary result) and the Analytics module (as one of the
 * aggregated signals an analytics query can surface) so the two features
 * never disagree on what a sentiment reading looks like.
 */
public class SentimentScoreDto {

    private SentimentLabel label;
    /** Continuous score, -1.0 (very negative) to 1.0 (very positive). */
    private double score;
    /** Model confidence in this reading, 0.0 to 1.0. */
    private double confidence;

    public SentimentScoreDto() {
    }

    public SentimentScoreDto(SentimentLabel label, double score, double confidence) {
        this.label = label;
        this.score = score;
        this.confidence = confidence;
    }

    public SentimentLabel getLabel() { return label; }
    public void setLabel(SentimentLabel label) { this.label = label; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    @Override
    public String toString() {
        return "SentimentScoreDto{" +
                "label=" + label +
                ", score=" + score +
                ", confidence=" + confidence +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SentimentScoreDto)) return false;
        SentimentScoreDto that = (SentimentScoreDto) o;
        return Double.compare(that.score, score) == 0 &&
                Double.compare(that.confidence, confidence) == 0 && label == that.label;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, score, confidence);
    }
}
