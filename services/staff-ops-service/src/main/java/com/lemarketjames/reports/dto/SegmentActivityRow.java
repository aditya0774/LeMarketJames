package com.lemarketjames.reports.dto;

import java.math.BigDecimal;

/** Only group aggregates cross the API boundary; individual identifiers stay in the database. */
public record SegmentActivityRow(String segment, long activeClients, long tradeCount,
                                 BigDecimal totalTradedValue) {}
