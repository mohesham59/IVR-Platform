package com.nexusivr.ai.dto;

public class VolumeDto {
    private String time;
    private int inbound;
    private int outbound;

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public int getInbound() { return inbound; }
    public void setInbound(int inbound) { this.inbound = inbound; }

    public int getOutbound() { return outbound; }
    public void setOutbound(int outbound) { this.outbound = outbound; }
}
