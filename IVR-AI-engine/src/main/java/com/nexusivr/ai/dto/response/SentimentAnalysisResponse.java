package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.SentimentScoreDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Output of the Sentiment module. `overall` is always populated (the
 * single reading for MESSAGE scope, or the aggregate for SESSION scope).
 * `perMessageScores` is only populated for SESSION-scope requests — one
 * entry per turn, in transcript order — so a caller can plot sentiment
 * drift across a conversation instead of only seeing the final rollup.
 */
public class SentimentAnalysisResponse {

    private SentimentScoreDto overall;
    private List<SentimentScoreDto> perMessageScores;

    public SentimentAnalysisResponse() {
        this.perMessageScores = new ArrayList<>();
    }

    public SentimentAnalysisResponse(SentimentScoreDto overall, List<SentimentScoreDto> perMessageScores) {
        this.overall = overall;
        this.perMessageScores = perMessageScores != null ? perMessageScores : new ArrayList<>();
    }

    public SentimentScoreDto getOverall() { return overall; }
    public void setOverall(SentimentScoreDto overall) { this.overall = overall; }

    public List<SentimentScoreDto> getPerMessageScores() { return perMessageScores; }
    public void setPerMessageScores(List<SentimentScoreDto> perMessageScores) { this.perMessageScores = perMessageScores; }

    @Override
    public String toString() {
        return "SentimentAnalysisResponse{" +
                "overall=" + overall +
                ", perMessageScores=" + perMessageScores +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SentimentAnalysisResponse)) return false;
        SentimentAnalysisResponse that = (SentimentAnalysisResponse) o;
        return Objects.equals(overall, that.overall) && Objects.equals(perMessageScores, that.perMessageScores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overall, perMessageScores);
    }
}
