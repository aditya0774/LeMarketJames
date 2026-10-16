package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * When the overnight reports run (LMKT-45). Two triggers a night, both in the reports time zone
 * and both from {@code PlatformSettings.Reports} (contract C5): the run time starts the night's
 * runs, outside trading hours, and the ready-by time fails any run not finished by then.
 *
 * <p>{@code reporting.overnight.enabled=false} switches both off, e.g. for a second instance that
 * should only serve reports.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "reporting.overnight.enabled", havingValue = "true", matchIfMissing = true)
public class OvernightScheduleConfig implements SchedulingConfigurer {

    private final OvernightRunner runner;
    private final PlatformSettings.Reports settings;
    private final ReportCalendar calendar;

    public OvernightScheduleConfig(OvernightRunner runner, PlatformSettings platformSettings, ReportCalendar calendar) {
        this.runner = runner;
        this.settings = platformSettings.getReports();
        this.calendar = calendar;
        // Checked here so a wrong pair of settings stops the service at startup, not at 2 a.m.
        requireRunBeforeReadyBy(settings);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(runner::runNight, runTrigger());
        registrar.addTriggerTask(runner::enforceDeadline, deadlineTrigger());
        // Once, as soon as the scheduler starts: a night the service was down for still shows in the list.
        registrar.addOneTimeTask(runner::catchUp, Duration.ZERO);
    }

    /** Starts each night's runs. */
    DailyTrigger runTrigger() {
        return new DailyTrigger(settings.getOvernightRunTime(), calendar);
    }

    /** Fails each night's unfinished runs. */
    DailyTrigger deadlineTrigger() {
        return new DailyTrigger(settings.getReadyBy(), calendar);
    }

    /** Runs and their deadline are the same night, so the deadline must come after the start. */
    static void requireRunBeforeReadyBy(PlatformSettings.Reports settings) {
        if (!settings.getOvernightRunTime().isBefore(settings.getReadyBy())) {
            throw new IllegalStateException("lmj.reports.overnight-run-time (" + settings.getOvernightRunTime()
                    + ") must be before lmj.reports.ready-by (" + settings.getReadyBy() + ")");
        }
    }
}
