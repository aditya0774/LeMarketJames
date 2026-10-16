package com.lemarketjames.reports.overnight;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LMKT-45 on a real PostgreSQL with migration 022 applied: the run writer can record runs although
 * every connection of this service is read-only by default, a report runs once a night, a run only
 * moves forward, and every other write is still refused.
 *
 * <p>Runs in Jenkins' integration stage; locally, with a database from setup-db.ps1 and 022 applied:
 * {@code mvn -B -pl services/reporting-service -am -Dspring.profiles.active=postgres-test
 * -Dtest=OvernightRunsIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test}.
 */
// The scheduler stays off: the test drives the writer itself and must not race a real run.
@SpringBootTest(properties = "reporting.overnight.enabled=false")
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*postgres-test.*")
class OvernightRunsIntegrationTest {

    /** Every report name these tests use starts with this, so cleanup touches nothing else. */
    private static final String PREFIX = "LMKT-45 integration test ";
    /** PostgreSQL's code for a write in a read-only transaction. */
    private static final String READ_ONLY_TRANSACTION = "25006";

    private static final LocalDate RUN_DATE = LocalDate.parse("2026-10-14");
    private static final Instant SCHEDULED_FOR = Instant.parse("2026-10-15T06:00:00Z");
    private static final Instant READY_BY = Instant.parse("2026-10-15T11:00:00Z");

    @Autowired ReportRunWriter writer;
    @Autowired StubOvernightReportJob stub;
    @Autowired ReportRunRepository runs;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private final String report = PREFIX + UUID.randomUUID();

    @AfterEach
    void removeThisTestsRuns() {
        // Cleanup is a write too, so it needs the same read-write transaction as the writer.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.execute("SET TRANSACTION READ WRITE");
            jdbc.update("DELETE FROM report_runs WHERE report_name LIKE ?", PREFIX + "%");
        });
    }

    private ReportRun stored(UUID id) {
        return runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(RUN_DATE).stream()
                .filter(run -> run.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theWriterRecordsARunFromScheduledToReady() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        assertEquals(RunStatus.SCHEDULED, stored(id).getStatus());

        assertTrue(writer.start(id, SCHEDULED_FOR));
        Instant readyAt = Instant.parse("2026-10-15T06:04:12Z");
        assertTrue(writer.finish(id, readyAt));

        ReportRun run = stored(id);
        assertEquals(RunStatus.READY, run.getStatus());
        assertEquals(readyAt, run.getReadyAt());
        assertEquals(SCHEDULED_FOR, run.getStartedAt());
        assertEquals(READY_BY, run.getReadyBy());
    }

    @Test
    void aFailedRunKeepsItsReason() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.start(id, SCHEDULED_FOR);

        assertTrue(writer.fail(id, "Source data missing"));

        assertEquals(RunStatus.FAILED, stored(id).getStatus());
        assertEquals("Source data missing", stored(id).getFailureReason());
    }

    @Test
    void aReportRunsOnceANight() {
        assertTrue(writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).isPresent());

        Optional<UUID> again = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY);

        assertTrue(again.isEmpty(), "a second run was created for the same night");
    }

    @Test
    void aRunFinishingAfterItsReadyByTimeIsLate() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.start(id, SCHEDULED_FOR);
        Instant finished = READY_BY.plusSeconds(720);

        assertTrue(writer.finish(id, finished));

        assertEquals(RunStatus.LATE, stored(id).getStatus());
        assertEquals(finished, stored(id).getReadyAt());
    }

    @Test
    void aRunFinishingExactlyAtItsReadyByTimeIsReady() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.start(id, SCHEDULED_FOR);

        writer.finish(id, READY_BY);

        assertEquals(RunStatus.READY, stored(id).getStatus());
    }

    @Test
    void theDeadlineMarksARunningRunLateAndItStaysLateWhenItFinishes() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.start(id, SCHEDULED_FOR);

        assertTrue(writer.markLate(READY_BY) >= 1);
        assertEquals(RunStatus.LATE, stored(id).getStatus());

        assertTrue(writer.finish(id, READY_BY.plusSeconds(60)));
        assertEquals(RunStatus.LATE, stored(id).getStatus());
        assertEquals(READY_BY.plusSeconds(60), stored(id).getReadyAt());
        assertFalse(writer.fail(id, "too late to fail"), "a finished run changed again");
    }

    @Test
    void aRunTheDeadlineMadeLateBeforeItStartedCanStillRunAndFail() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.markLate(READY_BY);

        assertTrue(writer.start(id, READY_BY.plusSeconds(1)), "a late run must still be able to start");
        assertEquals(RunStatus.LATE, stored(id).getStatus());
        assertTrue(writer.fail(id, "Source data missing"));

        assertEquals(RunStatus.FAILED, stored(id).getStatus());
        assertEquals("Source data missing", stored(id).getFailureReason());
    }

    @Test
    void theDeadlineLeavesRunsBeforeTheirReadyByTimeAlone() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();

        writer.markLate(READY_BY.minusSeconds(1));

        assertEquals(RunStatus.SCHEDULED, stored(id).getStatus());
    }

    @Test
    void aMissedNightIsRecordedAsFailedWithItsReason() {
        assertTrue(writer.recordMissed(report, RUN_DATE, SCHEDULED_FOR, READY_BY, "Did not run"));

        ReportRun run = runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(RUN_DATE).stream()
                .filter(r -> r.getReportName().equals(report)).findFirst().orElseThrow();
        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals("Did not run", run.getFailureReason());
    }

    @Test
    void aNightThatRanIsNeverRecordedAsMissed() {
        UUID id = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();

        assertFalse(writer.recordMissed(report, RUN_DATE, SCHEDULED_FOR, READY_BY, "Did not run"));
        assertEquals(RunStatus.SCHEDULED, stored(id).getStatus());
    }

    @Test
    void anInterruptedRunIsFailedButAFinishedOneIsLeftAlone() {
        UUID running = writer.schedule(report, RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.start(running, SCHEDULED_FOR);
        UUID finished = writer.schedule(report + " done", RUN_DATE, SCHEDULED_FOR, READY_BY).orElseThrow();
        writer.start(finished, SCHEDULED_FOR);
        writer.finish(finished, SCHEDULED_FOR.plusSeconds(60));

        assertTrue(writer.failInterrupted("Interrupted") >= 1);

        assertEquals(RunStatus.FAILED, stored(running).getStatus());
        assertEquals("Interrupted", stored(running).getFailureReason());
        assertEquals(RunStatus.READY, stored(finished).getStatus());
    }

    @Test
    void theStubReadsTheRealReportingView() {
        assertDoesNotThrow(() -> stub.run(RUN_DATE));
    }

    @Test
    void anyOtherWriteIsStillRefused() {
        // Outside the writer, the connection's read-only default still applies, report_runs included.
        DataAccessException refusal = assertThrows(DataAccessException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> jdbc.update("UPDATE report_runs SET status = status WHERE false")));
        SQLException cause = assertInstanceOf(SQLException.class, refusal.getMostSpecificCause());
        assertEquals(READ_ONLY_TRANSACTION, cause.getSQLState(), cause.getMessage());
    }
}
