package com.lemarketjames.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Contract C5: every setting has a default, and environments can override each one. */
class PlatformSettingsTest {

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        PlatformSettings settings = bind(Map.of());

        assertEquals(3, settings.getAuth().getLockout().getMaxAttempts());
        assertEquals(Duration.ofSeconds(30), settings.getAuth().getLockout().getDuration());
        assertEquals(Duration.ofMinutes(30), settings.getSession().getInactivityTimeout());
        assertEquals(Duration.ofSeconds(60), settings.getMarket().getStalenessLimit());
        assertEquals(List.of(), settings.getOrders().getRestrictedLocations());
        assertEquals(Duration.ofDays(365), settings.getAudit().getOnlineRetention());
        assertEquals(LocalTime.of(2, 0), settings.getReports().getOvernightRunTime());
    }

    @Test
    void propertiesOverrideDefaults() {
        PlatformSettings settings = bind(Map.of(
                "lmj.auth.lockout.max-attempts", "5",
                "lmj.market.staleness-limit", "15s",
                "lmj.orders.restricted-locations", "New York,Texas"));

        assertEquals(5, settings.getAuth().getLockout().getMaxAttempts());
        assertEquals(Duration.ofSeconds(15), settings.getMarket().getStalenessLimit());
        assertEquals(List.of("New York", "Texas"), settings.getOrders().getRestrictedLocations());
    }

    private static PlatformSettings bind(Map<String, String> properties) {
        PlatformSettings settings = new PlatformSettings();
        new Binder(new MapConfigurationPropertySource(properties))
                .bind("lmj", org.springframework.boot.context.properties.bind.Bindable.ofInstance(settings));
        return settings;
    }
}
