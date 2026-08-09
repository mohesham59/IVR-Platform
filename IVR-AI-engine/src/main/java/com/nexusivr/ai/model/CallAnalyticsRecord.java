package com.nexusivr.ai.model;

import java.sql.Timestamp;

public class CallAnalyticsRecord {
    private int id;
    private String callId;
    private String callerNumber;
    private Timestamp startTime;
    private Timestamp endTime;
    private int duration;
    private String events; // JSON String
    private String recordingUrl;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    
    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
    
    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }
    
    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }
    
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    
    public String getEvents() { return events; }
    public void setEvents(String events) { this.events = events; }
    
    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }
}
