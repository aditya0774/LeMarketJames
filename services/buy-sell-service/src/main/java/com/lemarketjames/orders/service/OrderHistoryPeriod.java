package com.lemarketjames.orders.service;

import java.time.*;

/** Converts a client's calendar period to a half-open UTC range for stored order timestamps. */
public record OrderHistoryPeriod(LocalDateTime start, LocalDateTime end) {
    public static OrderHistoryPeriod parse(String date, String timeZone) {
        if (date == null && timeZone == null) return null;
        if (date == null || timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("date and timeZone must be supplied together");
        }
        try {
            ZoneId zone = ZoneId.of(timeZone);
            LocalDate start;
            LocalDate end;
            if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                start = LocalDate.parse(date);
                end = start.plusDays(1);
            } else if (date.matches("[0-9]{4}-[0-9]{2}")) {
                start = YearMonth.parse(date).atDay(1);
                end = start.plusMonths(1);
            } else if (date.matches("[0-9]{4}")) {
                start = Year.parse(date).atDay(1);
                end = start.plusYears(1);
            } else {
                throw new IllegalArgumentException("date must be YYYY-MM-DD, YYYY-MM or YYYY");
            }
            if (start.getYear() < 1) throw new IllegalArgumentException("date year must be positive");
            // Resolve both local midnights independently: DST days need not contain 24 hours.
            return new OrderHistoryPeriod(
                LocalDateTime.ofInstant(start.atStartOfDay(zone).toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(end.atStartOfDay(zone).toInstant(), ZoneOffset.UTC));
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid date or timeZone", ex);
        }
    }
}
