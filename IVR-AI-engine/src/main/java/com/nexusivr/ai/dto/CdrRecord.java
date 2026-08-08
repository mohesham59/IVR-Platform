package com.nexusivr.ai.dto;

/**
 * Data Transfer Object for a single Asterisk CDR record (from cdr_csv Master.csv).
 */
public class CdrRecord {

    private String uniqueId;
    private String caller;
    private String callee;
    private String start;
    private String answer;
    private long durationSec;
    private long billsec;
    private String disposition;
    private String status;

    public CdrRecord() {
    }

    public CdrRecord(String uniqueId, String caller, String callee, String start, String answer,
                     long durationSec, long billsec, String disposition, String status) {
        this.uniqueId = uniqueId;
        this.caller = caller;
        this.callee = callee;
        this.start = start;
        this.answer = answer;
        this.durationSec = durationSec;
        this.billsec = billsec;
        this.disposition = disposition;
        this.status = status;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getCallee() {
        return callee;
    }

    public void setCallee(String callee) {
        this.callee = callee;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public long getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(long durationSec) {
        this.durationSec = durationSec;
    }

    public long getBillsec() {
        return billsec;
    }

    public void setBillsec(long billsec) {
        this.billsec = billsec;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
