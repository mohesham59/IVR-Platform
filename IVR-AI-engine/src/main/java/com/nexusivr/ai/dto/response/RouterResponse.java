package com.nexusivr.ai.dto.response;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Output of the Router module: the chosen next node/route id, its
 * confidence, and the scores for every other candidate that was
 * considered (keyed by route id) so a caller can implement fallback
 * logic (e.g. "if top two are within 0.05 of each other, ask a
 * clarifying question instead of routing blindly") without a second call.
 */
public class RouterResponse {

    private String chosenRouteId;
    private double confidenceScore;
    private Map<String, Double> alternativeScores;
    private String reasoning;

    public RouterResponse() {
        this.alternativeScores = new HashMap<>();
    }

    public RouterResponse(String chosenRouteId, double confidenceScore, Map<String, Double> alternativeScores,
                           String reasoning) {
        this.chosenRouteId = chosenRouteId;
        this.confidenceScore = confidenceScore;
        this.alternativeScores = alternativeScores != null ? alternativeScores : new HashMap<>();
        this.reasoning = reasoning;
    }

    public String getChosenRouteId() { return chosenRouteId; }
    public void setChosenRouteId(String chosenRouteId) { this.chosenRouteId = chosenRouteId; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Map<String, Double> getAlternativeScores() { return alternativeScores; }
    public void setAlternativeScores(Map<String, Double> alternativeScores) { this.alternativeScores = alternativeScores; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    @Override
    public String toString() {
        return "RouterResponse{" +
                "chosenRouteId='" + chosenRouteId + '\'' +
                ", confidenceScore=" + confidenceScore +
                ", alternativeScores=" + alternativeScores +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouterResponse)) return false;
        RouterResponse that = (RouterResponse) o;
        return Double.compare(that.confidenceScore, confidenceScore) == 0 &&
                Objects.equals(chosenRouteId, that.chosenRouteId) &&
                Objects.equals(alternativeScores, that.alternativeScores) &&
                Objects.equals(reasoning, that.reasoning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chosenRouteId, confidenceScore, alternativeScores, reasoning);
    }
}
