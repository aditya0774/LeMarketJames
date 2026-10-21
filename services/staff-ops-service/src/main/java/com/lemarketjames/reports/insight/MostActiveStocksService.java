package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Computes the current reports-week stock ranking on every request, and flags a stock whose
 * traded value moved enough against the same days of last week (BR-17).
 */
@Service
public class MostActiveStocksService {

    private final MostActiveStocksRepository repository;
    private final ReportCalendar calendar;
    private final InsightFlagRule flagRule;
    private final Clock clock;

    public MostActiveStocksService(MostActiveStocksRepository repository, ReportCalendar calendar,
                                   InsightFlagRule flagRule, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.flagRule = flagRule;
        this.clock = clock;
    }

    public MostActiveStocksResponse currentWeek() {
        LocalDate today = LocalDate.now(clock.withZone(calendar.zone()));
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        ReportPeriod week = calendar.between(monday, today);
        // The week so far against the same days of last week: a whole week before would make every
        // stock look like it dropped early in the week.
        ReportPeriod weekBefore = calendar.between(monday.minusWeeks(1), today.minusWeeks(1));

        List<MostActiveStocksRepository.MostActiveStockAggregate> rows =
            repository.findTopFive(week.start(), week.end());
        Map<String, BigDecimal> before = previousValues(rows, weekBefore);
        List<MostActiveStock> stocks = IntStream.range(0, rows.size())
                .mapToObj(index -> {
                    MostActiveStocksRepository.MostActiveStockAggregate row = rows.get(index);
                    BigDecimal previous = before.getOrDefault(row.getSymbol(), BigDecimal.ZERO);
                    return new MostActiveStock(index + 1, row.getSymbol(), row.getTradedValue(),
                            row.getTradeCount(), row.getShareVolume(), previous,
                            flagRule.judge(row.getTradedValue(), previous));
                })
                .toList();
        return new MostActiveStocksResponse(week.firstDay(), today, weekBefore.firstDay(), today.minusWeeks(1),
                stocks, Instant.now(clock));
    }

    /** Last week's traded value of this week's top stocks; none asked for when nothing traded. */
    private Map<String, BigDecimal> previousValues(List<MostActiveStocksRepository.MostActiveStockAggregate> rows,
                                                   ReportPeriod weekBefore) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<String> symbols = rows.stream().map(MostActiveStocksRepository.MostActiveStockAggregate::getSymbol).toList();
        return repository.tradedValueOf(symbols, weekBefore.start(), weekBefore.end()).stream()
                .collect(Collectors.toMap(MostActiveStocksRepository.SymbolTradedValue::getSymbol,
                        MostActiveStocksRepository.SymbolTradedValue::getTradedValue));
    }
}
