package com.nexusivr.ai.dto;

import java.util.List;

public class AnalyticsPayload {
    private int liveCalls;
    private List<CallLogDto> recentCalls;
    private List<VolumeDto> callVolume;
    private List<DistributionDto> callDist;

    public AnalyticsPayload(int liveCalls, List<CallLogDto> recentCalls, List<VolumeDto> callVolume, List<DistributionDto> callDist) {
        this.liveCalls = liveCalls;
        this.recentCalls = recentCalls;
        this.callVolume = callVolume;
        this.callDist = callDist;
    }

    public int getLiveCalls() { return liveCalls; }
    public void setLiveCalls(int liveCalls) { this.liveCalls = liveCalls; }

    public List<CallLogDto> getRecentCalls() { return recentCalls; }
    public void setRecentCalls(List<CallLogDto> recentCalls) { this.recentCalls = recentCalls; }

    public List<VolumeDto> getCallVolume() { return callVolume; }
    public void setCallVolume(List<VolumeDto> callVolume) { this.callVolume = callVolume; }

    public List<DistributionDto> getCallDist() { return callDist; }
    public void setCallDist(List<DistributionDto> callDist) { this.callDist = callDist; }
}
