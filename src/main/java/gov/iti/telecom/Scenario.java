package gov.iti.telecom;

public class Scenario {
    private String businessId;
    private String scenarioId;

    public Scenario(String businessId, String scenarioId) {
        this.businessId = businessId;
        this.scenarioId = scenarioId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getScenarioId() {
        return scenarioId;
    }
}
