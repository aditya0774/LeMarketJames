package com.lemarketjames.reports.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response envelope for trade aggregation reports.
 * Contains aggregated data by period and generation timestamp.
 */
public class TradeReportResponse {

    private List<PeriodAggregation> data;
    private LocalDateTime generatedAt;

    public TradeReportResponse() {
    }

    public TradeReportResponse(List<PeriodAggregation> data, LocalDateTime generatedAt) {
        this.data = data;
        this.generatedAt = generatedAt;
    }

    public List<PeriodAggregation> getData() {
        return data;
    }

    public void setData(List<PeriodAggregation> data) {
        this.data = data;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
