package com.nexusivr.ai.dto;

public class CallLogDto {
    private String caller;
    private String status;
    private String duration;
    private String scenario;

    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
}
