package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Computes the current reports-week stock ranking on every request. */
@Service
public class MostActiveStocksService {

    private final MostActiveStocksRepository repository;
    private final ReportCalendar calendar;
    private final Clock clock;

    public MostActiveStocksService(MostActiveStocksRepository repository, ReportCalendar calendar, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.clock = clock;
    }

    public MostActiveStocksResponse currentWeek() {
        LocalDate today = LocalDate.now(clock.withZone(calendar.zone()));
        ReportPeriod week = calendar.between(today.minusDays(today.getDayOfWeek().getValue() - 1L), today);
        List<MostActiveStocksRepository.MostActiveStockAggregate> rows =
            repository.findTopFive(week.start(), week.end());
        List<MostActiveStock> stocks = IntStream.range(0, rows.size())
                .mapToObj(index -> {
                MostActiveStocksRepository.MostActiveStockAggregate row = rows.get(index);
                    return new MostActiveStock(index + 1, row.getSymbol(), row.getTradedValue(),
                            row.getTradeCount(), row.getShareVolume());
                })
                .toList();
        return new MostActiveStocksResponse(week.firstDay(), today, stocks, Instant.now(clock));
    }
}