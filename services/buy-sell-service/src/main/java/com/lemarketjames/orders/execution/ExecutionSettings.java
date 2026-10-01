package com.lemarketjames.orders.execution;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Execution controls; defaults are defined here rather than copied into deployment files. */
@Component("executionSettings")
@ConfigurationProperties("lmj.execution")
public class ExecutionSettings {
    private boolean enabled = true;
    private boolean respectMarketHours = true;
    private long pollMs = 1000;
    private Set<LocalDate> holidays = new HashSet<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isRespectMarketHours() { return respectMarketHours; }
    public void setRespectMarketHours(boolean value) { respectMarketHours = value; }
    public long getPollMs() { return pollMs; }
    public void setPollMs(long value) {
        if (value < 100) throw new IllegalArgumentException("Execution polling must be at least 100ms");
        pollMs = value;
    }
    public Set<LocalDate> getHolidays() { return holidays; }
    public void setHolidays(Set<LocalDate> value) { holidays = value; }
}
