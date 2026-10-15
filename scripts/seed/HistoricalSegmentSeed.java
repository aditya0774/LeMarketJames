package com.lemarketjames.common.audit;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.orders.submission.ValidationRule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Opt-in fixture writer, outside all application source trees. No live quote or event publication. */
@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@EntityScan(basePackageClasses = {AccountEntity.class, AuditEventEntity.class})
@EnableJpaRepositories(basePackageClasses = {AccountRepository.class, AuditEventRepository.class})
public class HistoricalSegmentSeed {
    private static final String SOURCE = "DEMO_HISTORY_2026";
    private static final String[] SYMBOLS = {"AAPL", "MSFT", "NVDA", "AMZN", "JPM", "KO"};
    // Synthetic reference prices, not a claim about actual historical market quotes.
    private static final int[] PRICES = {210, 420, 130, 190, 230, 65};
    private static final int[][] ACTIVE = {{15, 16, 17, 18}, {7, 8, 7, 8}, {3, 3, 4, 4}};
    private static final int[][] PAIRS = {{2, 2, 3, 3}, {6, 7, 6, 7}, {3, 3, 4, 4}};
    // Per group: demo client numbers start after GROUP_OFFSET, and trade sizes grow by QUANTITY_STEP per member.
    private static final int[] GROUP_OFFSET = {0, 18, 26};
    private static final int[] BASE_QUANTITY = {3, 15, 120};
    private static final int[] QUANTITY_STEP = {2, 2, 12};
    private static final Set<LocalDate> HOLIDAYS = Set.of(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 7, 3), LocalDate.of(2026, 9, 7));

    static class SeedClock extends Clock {
        Instant now = Instant.parse("2026-06-01T14:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @Bean SeedClock seedClock() { return new SeedClock(); }
    @Bean AuditRecorder recorder(AuditEventRepository events, AccountRepository accounts, SeedClock clock) {
        return new AuditRecorder(events, accounts, clock);
    }

    public static void main(String[] args) {
        boolean apply = Arrays.asList(args).contains("--apply");
        SpringApplication app = new SpringApplication(HistoricalSegmentSeed.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.datasource.url", env("DEMO_DATABASE_URL", "jdbc:postgresql://localhost:5432/lemarket"),
                "spring.datasource.username", env("DEMO_DATABASE_USER", "lemarket"),
                "spring.datasource.password", env("DB_PASSWORD", "changeme"),
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.jdbc.time_zone", "UTC",
                "spring.jpa.open-in-view", "false",
                "logging.level.root", "WARN", "spring.main.banner-mode", "off"));
        try (var context = app.run("--spring.config.name=historical-seed")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('historical_segment_demo_2026'))");
                int existing = jdbc.queryForObject("SELECT count(*) FROM orders WHERE quote_source = ?", Integer.class, SOURCE);
                if (existing > 0) {
                    verify(jdbc);
                    System.out.println("Historical demo already present; verified, no changes made.");
                    return;
                }
                if (jdbc.queryForObject("SELECT count(*) FROM clients WHERE username ~ '^demo_history_[0-9]{2}$'", Integer.class) != 30) {
                    throw new IllegalStateException("Apply database/schema/021_historical_segment_demo_clients.sql first");
                }
                if (jdbc.queryForObject("SELECT count(*) FROM orders o JOIN accounts a USING(account_id) JOIN clients c USING(client_id) WHERE c.username ~ '^demo_history_[0-9]{2}$'", Integer.class) != 0) {
                    throw new IllegalStateException("Historical demo accounts already contain other orders; refusing to alter them");
                }
                var writer = new HistoricalSegmentSeed();
                writer.populate(jdbc, context.getBean(AuditRecorder.class), context.getBean(SeedClock.class));
                verify(jdbc);
                System.out.println(apply ? "Historical demo verified; committing transaction." : "Dry run verified; rolling back all trades, holdings and cash changes.");
                if (!apply) status.setRollbackOnly();
            });
        }
    }

    private void populate(JdbcTemplate jdbc, AuditRecorder audit, SeedClock clock) {
        int total = 0;
        for (int month = 0; month < 4; month++) {
            List<LocalDate> days = tradingDays(LocalDate.of(2026, month + 6, 1));
            for (int group = 0; group < 3; group++) {
                for (int member = 1; member <= ACTIVE[group][month]; member++) {
                    total += populateMember(jdbc, audit, clock, days, month, group, member);
                }
            }
        }
        System.out.println("Created " + total + " synthetic filled orders across June-September 2026.");
    }

    /** Weekdays of the month starting on {@code first}, without its market holidays. */
    private static List<LocalDate> tradingDays(LocalDate first) {
        return first.datesUntil(first.plusMonths(1))
                .filter(d -> d.getDayOfWeek().getValue() <= 5)
                .filter(d -> !HOLIDAYS.contains(d))
                .toList();
    }

    /** Writes one demo client's round trips for one month and returns the number of orders created. */
    private int populateMember(JdbcTemplate jdbc, AuditRecorder audit, SeedClock clock, List<LocalDate> days,
                               int month, int group, int member) {
        int person = GROUP_OFFSET[group] + member;
        var account = jdbc.queryForMap("SELECT a.account_id, c.client_id FROM accounts a JOIN clients c USING(client_id) WHERE c.username = ?",
                "demo_history_" + String.format("%02d", person));
        int accountId = ((Number) account.get("account_id")).intValue();
        int clientId = ((Number) account.get("client_id")).intValue();
        int count = PAIRS[group][month] + member % 2;
        for (int pair = 0; pair < count; pair++) {
            int index = (person + pair + month) % SYMBOLS.length;
            int instrument = jdbc.queryForObject("SELECT instrument_id FROM instruments WHERE ticker = ? AND tradable", Integer.class, SYMBOLS[index]);
            BigDecimal quantity = BigDecimal.valueOf(BASE_QUANTITY[group] + member * QUANTITY_STEP[group] + pair % 3);
            BigDecimal buyPrice = BigDecimal.valueOf(PRICES[index])
                    .multiply(BigDecimal.valueOf(1000 + month * 12 + (member % 5) * 3 + pair * 2, 3)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellPrice = buyPrice.multiply(BigDecimal.valueOf(1000 + ((person + pair) % 7 - 3) * 4, 3))
                    .setScale(2, RoundingMode.HALF_UP);
            // Each position closes before the next opens: no hidden short positions or invented opening holdings.
            LocalDate day = days.get(pair * days.size() / count);
            Instant time = day.atTime(14 + person % 3, (person * 7) % 60).toInstant(ZoneOffset.UTC);
            trade(jdbc, audit, clock, accountId, clientId, instrument, "BUY", quantity, buyPrice, time);
            trade(jdbc, audit, clock, accountId, clientId, instrument, "SELL", quantity, sellPrice, time.plusSeconds(1800));
        }
        return 2 * count;
    }

    private void trade(JdbcTemplate jdbc, AuditRecorder audit, SeedClock clock, int account, int client,
                       int instrument, String side, BigDecimal quantity, BigDecimal price, Instant submitted) {
        BigDecimal delta = quantity.multiply(price).multiply(BigDecimal.valueOf(side.equals("BUY") ? -1 : 1));
        BigDecimal shares = quantity.multiply(BigDecimal.valueOf(side.equals("BUY") ? 1 : -1));
        int order = jdbc.queryForObject("""
                INSERT INTO orders(account_id,instrument_id,order_type,quantity,price_per_unit,order_status,
                    submitted_at,accepted_at,filled_at,created_at,updated_at,quote_source,quote_time)
                VALUES(?,?,?,?,?,'FILLED',?,?,?,?,?,?,?) RETURNING order_id
                """, Integer.class, account, instrument, side, quantity, price, Timestamp.from(submitted),
                Timestamp.from(submitted.plusSeconds(10)), Timestamp.from(submitted.plusSeconds(12)),
                Timestamp.from(submitted), Timestamp.from(submitted.plusSeconds(12)), SOURCE, Timestamp.from(submitted));
        clock.now = submitted;
        String request = UUID.nameUUIDFromBytes((SOURCE + ":" + order).getBytes(StandardCharsets.UTF_8)).toString();
        audit.recordSubmission(new SubmissionAuditEvent(request, AuditEventType.SUBMITTED, null, order, account, client,
                Map.of("side", side, "quantity", quantity, "price", price, "instrumentId", instrument, "synthetic", true)));
        var rules = Arrays.stream(ValidationRule.values()).filter(rule -> side.equals("BUY")
                ? rule != ValidationRule.HOLDINGS
                : rule != ValidationRule.PRICE_AVAILABLE && rule != ValidationRule.QUOTE_FRESH && rule != ValidationRule.CASH).toList();
        for (var rule : rules) {
            clock.now = clock.now.plusMillis(100);
            audit.recordSubmission(new SubmissionAuditEvent(request, AuditEventType.RULE_CHECKED, rule.name(), order, account, client,
                    Map.of("rule", rule.name(), "result", "PASS", "synthetic", true)));
        }
        clock.now = submitted.plusSeconds(2);
        audit.recordSubmission(new SubmissionAuditEvent(request, AuditEventType.VALIDATED, null, order, account, client,
                Map.of("checks", rules.stream().map(Enum::name).toList(), "synthetic", true)));
        clock.now = submitted.plusSeconds(10);
        audit.record(AuditEventType.ACCEPTED, order, account, Map.of("synthetic", true));
        jdbc.update("UPDATE accounts SET cash_balance = cash_balance + ?, updated_at = ? WHERE account_id = ?",
                delta, Timestamp.from(submitted.plusSeconds(11)), account);
        jdbc.update("""
                INSERT INTO holdings(account_id,instrument_id,quantity,average_cost,last_updated) VALUES(?,?,?,?,?)
                ON CONFLICT(account_id,instrument_id) DO UPDATE SET
                  quantity=holdings.quantity + EXCLUDED.quantity,
                  average_cost=CASE WHEN holdings.quantity = 0 THEN EXCLUDED.average_cost ELSE holdings.average_cost END,
                  last_updated=EXCLUDED.last_updated
                """, account, instrument, shares, price, Timestamp.from(submitted.plusSeconds(11)));
        jdbc.update("INSERT INTO settlement_receipts(order_id,account_id,instrument_id,order_type,quantity,price_per_unit) VALUES(?,?,?,?,?,?)",
                order, account, instrument, side, quantity, price);
        clock.now = submitted.plusSeconds(11);
        audit.record(AuditEventType.SETTLED, order, account, Map.of("cashDelta", delta, "quantityDelta", shares, "synthetic", true));
        clock.now = submitted.plusSeconds(12);
        audit.record(AuditEventType.FILLED, order, account, Map.of("quantity", quantity, "price", price, "synthetic", true));
        if (jdbc.queryForObject("SELECT cash_balance FROM accounts WHERE account_id = ?", BigDecimal.class, account).signum() < 0
                || jdbc.queryForObject("SELECT quantity FROM holdings WHERE account_id = ? AND instrument_id = ?", BigDecimal.class, account, instrument).signum() < 0) {
            throw new IllegalStateException("Fixture exceeded available cash or holdings");
        }
    }

    private static void verify(JdbcTemplate jdbc) {
        int expected = 0;
        for (int month = 0; month < 4; month++) {
            for (int group = 0; group < 3; group++) {
                for (int member = 1; member <= ACTIVE[group][month]; member++) {
                    expected += 2 * (PAIRS[group][month] + member % 2);
                }
            }
        }
        if (jdbc.queryForObject("SELECT count(*) FROM orders WHERE quote_source = ?", Integer.class, SOURCE) != expected) {
            throw new IllegalStateException("Historical demo is incomplete; refusing to silently skip it");
        }
        int incompleteAudit = jdbc.queryForObject("""
                SELECT count(*) FROM orders o WHERE o.quote_source = ? AND
                  ((SELECT count(*) FROM audit_log a WHERE a.order_id = o.order_id) <
                    CASE WHEN o.order_type='BUY' THEN ? ELSE ? END
                   OR NOT EXISTS (SELECT 1 FROM audit_log a WHERE a.order_id=o.order_id AND a.action='FILLED')
                   OR NOT EXISTS (SELECT 1 FROM audit_log a WHERE a.order_id=o.order_id AND a.action='SETTLED'))
                """, Integer.class, SOURCE, ValidationRule.values().length - 1 + 5, ValidationRule.values().length - 3 + 5);
        if (incompleteAudit != 0) throw new IllegalStateException("Incomplete audit trail");
        int missing = jdbc.queryForObject("""
                SELECT count(*) FROM orders o LEFT JOIN settlement_receipts r USING(order_id)
                WHERE o.quote_source = ? AND (r.order_id IS NULL OR r.quantity <> o.quantity OR r.price_per_unit <> o.price_per_unit)
                """, Integer.class, SOURCE);
        if (missing != 0) throw new IllegalStateException("Inconsistent settlement receipts");
        jdbc.queryForList("""
                SELECT to_char(o.filled_at, 'YYYY-MM') AS month, c.segment,
                  count(DISTINCT c.client_id) AS clients, count(*) AS trades,
                  sum(o.quantity * o.price_per_unit) AS traded_value
                FROM orders o JOIN accounts a USING(account_id) JOIN clients c USING(client_id)
                WHERE o.quote_source = ? GROUP BY 1,2 ORDER BY 1,2
                """, SOURCE).forEach(System.out::println);
    }
    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
}
