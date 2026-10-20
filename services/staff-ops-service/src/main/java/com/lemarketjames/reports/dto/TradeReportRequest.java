package com.lemarketjames.reports.dto;

import java.time.LocalDate;

/**
 * Request to generate a trade aggregation report.
 * Query parameters: periodType (required), from/to (optional), timeZone (optional).
 */
public class TradeReportRequest {

    private String periodType;
    private LocalDate from;
    private LocalDate to;
    private String timeZone;

    public TradeReportRequest() {
    }

    public TradeReportRequest(String periodType, LocalDate from, LocalDate to, String timeZone) {
        this.periodType = periodType;
        this.from = from;
        this.to = to;
        this.timeZone = timeZone;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
