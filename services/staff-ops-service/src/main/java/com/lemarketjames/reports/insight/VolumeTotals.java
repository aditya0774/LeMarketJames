package com.lemarketjames.reports.insight;

import java.math.BigDecimal;

/**
 * What the database returns for one period: the totals over every filled order in it. Spring Data
 * maps the aliased columns of {@link VolumeInsightRepository}'s native query onto these getters.
 */
public interface VolumeTotals {
    long getTradeCount();

    BigDecimal getShareVolume();

    BigDecimal getGrossAmount();
}
