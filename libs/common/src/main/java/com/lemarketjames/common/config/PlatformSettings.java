package com.lemarketjames.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The platform's business settings (contract C5): the single place their keys, defaults and
 * meanings are defined. Open product questions are answered here as configuration instead of
 * being hard-coded, so changing an answer never needs a code change.
 *
 * <p>Every value can be overridden per environment without touching a properties file, through
 * Spring's relaxed binding: {@code lmj.auth.lockout.max-attempts} is set by the environment
 * variable {@code LMJ_AUTH_LOCKOUT_MAXATTEMPTS}. Don't re-declare these keys in
 * application.properties just to restate a default.
 *
 * <p>Available in every servlet service that depends on libs/common (auth, core, holdings).
 * Market hours and holidays belong to market-service instead: see its MarketSimulationProperties.
 */
@Component
@ConfigurationProperties(prefix = "lmj")
public class PlatformSettings {

    private final Session session = new Session();
    private final Auth auth = new Auth();
    private final Market market = new Market();
    private final Orders orders = new Orders();
    private final Audit audit = new Audit();
    private final Reports reports = new Reports();
    private final Surveillance surveillance = new Surveillance();

    public Session getSession() {
        return session;
    }

    public Auth getAuth() {
        return auth;
    }

    public Market getMarket() {
        return market;
    }

    public Orders getOrders() {
        return orders;
    }

    public Audit getAudit() {
        return audit;
    }

    public Reports getReports() {
        return reports;
    }

    public Surveillance getSurveillance() {
        return surveillance;
    }

    /** Logged-in sessions. */
    public static class Session {
        /** How long a session may sit idle before the user must log in again. */
        private Duration inactivityTimeout = Duration.ofMinutes(30);

        public Duration getInactivityTimeout() {
            return inactivityTimeout;
        }

        public void setInactivityTimeout(Duration inactivityTimeout) {
            this.inactivityTimeout = inactivityTimeout;
        }
    }

    /** Login protection. */
    public static class Auth {
        private final Lockout lockout = new Lockout();

        public Lockout getLockout() {
            return lockout;
        }

        /** Locks a login after repeated wrong passwords; applies to clients and staff alike. */
        public static class Lockout {
            /** Consecutive failed logins that lock the account. */
            private int maxAttempts = 3;
            /** How long a locked account stays locked. */
            private Duration duration = Duration.ofSeconds(30);

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public Duration getDuration() {
                return duration;
            }

            public void setDuration(Duration duration) {
                this.duration = duration;
            }
        }
    }

    /** How quotes are consumed (the feed itself is market-service's). */
    public static class Market {
        /**
         * Oldest a quote may be and still be used to price an order; older quotes are stale.
         * Check with QuoteFreshness in libs/market-client.
         */
        private Duration stalenessLimit = Duration.ofSeconds(5);

        public Duration getStalenessLimit() {
            return stalenessLimit;
        }

        public void setStalenessLimit(Duration stalenessLimit) {
            this.stalenessLimit = stalenessLimit;
        }
    }

    /** Order placement rules. */
    public static class Orders {
        /**
         * Client residence locations (US state names as stored in addresses.state, or ISO country
         * codes) that may not place orders. Empty means no restriction.
         */
        private List<String> restrictedLocations = new ArrayList<>();

        public List<String> getRestrictedLocations() {
            return restrictedLocations;
        }

        public void setRestrictedLocations(List<String> restrictedLocations) {
            this.restrictedLocations = restrictedLocations;
        }
    }

    /** The audit trail (audit_log). */
    public static class Audit {
        /**
         * How long audit events stay online (queryable in the app) before they count as
         * archived. Archived events are kept, not deleted, and their rows are not changed: an
         * event's age is what makes it archived (contract C2).
         */
        private Duration onlineRetention = Duration.ofDays(365);

        public Duration getOnlineRetention() {
            return onlineRetention;
        }

        public void setOnlineRetention(Duration onlineRetention) {
            this.onlineRetention = onlineRetention;
        }
    }

    /** Scheduled reporting. */
    public static class Reports {
        /** Local time of day the overnight reports run. */
        private LocalTime overnightRunTime = LocalTime.of(2, 0);
        /** Time zone overnightRunTime is in. */
        private ZoneId timeZone = ZoneId.of("America/New_York");

        public LocalTime getOvernightRunTime() {
            return overnightRunTime;
        }

        public void setOvernightRunTime(LocalTime overnightRunTime) {
            this.overnightRunTime = overnightRunTime;
        }

        public ZoneId getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(ZoneId timeZone) {
            this.timeZone = timeZone;
        }
    }

    /** Order surveillance: which new orders Trading Operations are alerted to. */
    public static class Surveillance {
        /**
         * Shares in one order at which it counts as large. An order of this many shares or more
         * raises an alert for Trading Operations when it is placed; a smaller one raises none.
         */
        private BigDecimal largeOrderQuantity = new BigDecimal("100");

        public BigDecimal getLargeOrderQuantity() {
            return largeOrderQuantity;
        }

        public void setLargeOrderQuantity(BigDecimal largeOrderQuantity) {
            this.largeOrderQuantity = largeOrderQuantity;
        }
    }
}
