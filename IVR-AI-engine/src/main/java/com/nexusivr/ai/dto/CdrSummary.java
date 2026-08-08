package com.nexusivr.ai.dto;

import java.util.List;

/**
 * Aggregate CDR analytics summary for dashboards and reports.
 */
public class CdrSummary {

    private int totalCalls;
    private int answered;
    private int abandoned;
    private double answeredRate;
    private double abandonedRate;
    private double avgDurationSec;
    private double avgBillsec;

    private List<CdrDayBucket> daily;
    private List<CdrHourBucket> hourly;

    public CdrSummary() {
    }

    public CdrSummary(int totalCalls, int answered, int abandoned, double answeredRate, double abandonedRate,
                      double avgDurationSec, double avgBillsec, List<CdrDayBucket> daily, List<CdrHourBucket> hourly) {
        this.totalCalls = totalCalls;
        this.answered = answered;
        this.abandoned = abandoned;
        this.answeredRate = answeredRate;
        this.abandonedRate = abandonedRate;
        this.avgDurationSec = avgDurationSec;
        this.avgBillsec = avgBillsec;
        this.daily = daily;
        this.hourly = hourly;
    }

    public int getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(int totalCalls) {
        this.totalCalls = totalCalls;
    }

    public int getAnswered() {
        return answered;
    }

    public void setAnswered(int answered) {
        this.answered = answered;
    }

    public int getAbandoned() {
        return abandoned;
    }

    public void setAbandoned(int abandoned) {
        this.abandoned = abandoned;
    }

    public double getAnsweredRate() {
        return answeredRate;
    }

    public void setAnsweredRate(double answeredRate) {
        this.answeredRate = answeredRate;
    }

    public double getAbandonedRate() {
        return abandonedRate;
    }

    public void setAbandonedRate(double abandonedRate) {
        this.abandonedRate = abandonedRate;
    }

    public double getAvgDurationSec() {
        return avgDurationSec;
    }

    public void setAvgDurationSec(double avgDurationSec) {
        this.avgDurationSec = avgDurationSec;
    }

    public double getAvgBillsec() {
        return avgBillsec;
    }

    public void setAvgBillsec(double avgBillsec) {
        this.avgBillsec = avgBillsec;
    }

    public List<CdrDayBucket> getDaily() {
        return daily;
    }

    public void setDaily(List<CdrDayBucket> daily) {
        this.daily = daily;
    }

    public List<CdrHourBucket> getHourly() {
        return hourly;
    }

    public void setHourly(List<CdrHourBucket> hourly) {
        this.hourly = hourly;
    }

    /** Per-day call bucket (answered/abandoned split). */
    public static class CdrDayBucket {
        private String day;
        private int calls;
        private int answered;
        private int abandoned;

        public CdrDayBucket() {
        }

        public CdrDayBucket(String day, int calls, int answered, int abandoned) {
            this.day = day;
            this.calls = calls;
            this.answered = answered;
            this.abandoned = abandoned;
        }

        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public int getCalls() {
            return calls;
        }

        public void setCalls(int calls) {
            this.calls = calls;
        }

        public int getAnswered() {
            return answered;
        }

        public void setAnswered(int answered) {
            this.answered = answered;
        }

        public int getAbandoned() {
            return abandoned;
        }

        public void setAbandoned(int abandoned) {
            this.abandoned = abandoned;
        }
    }

    /** Per-hour call bucket (0-23). */
    public static class CdrHourBucket {
        private int hour;
        private int calls;

        public CdrHourBucket() {
        }

        public CdrHourBucket(int hour, int calls) {
            this.hour = hour;
            this.calls = calls;
        }

        public int getHour() {
            return hour;
        }

        public void setHour(int hour) {
            this.hour = hour;
        }

        public int getCalls() {
            return calls;
        }

        public void setCalls(int calls) {
            this.calls = calls;
        }
    }
}
