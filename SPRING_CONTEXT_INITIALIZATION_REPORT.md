# Spring Context Initialization Analysis
## OwnDataIntegrationTest - ApplicationContext Loading Issues

---

## Summary
The application context initialization involves several configuration classes and startup components. Below are all components that execute during test context creation and could potentially cause "Failed to load ApplicationContext" errors.

---

## 1. @Configuration Classes

### SecurityConfig
**File:** [apps/backend/src/main/java/com/lemarketjames/config/SecurityConfig.java](apps/backend/src/main/java/com/lemarketjames/config/SecurityConfig.java#L25)
**Line:** 25

**Issue:** `@Value` injection for `app.cors.allowed-origin` at [line 29](apps/backend/src/main/java/com/lemarketjames/config/SecurityConfig.java#L29)
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;
```

**Potential Failure:** If `app.cors.allowed-origin` property is missing from `application.properties`, Spring will fail to inject the value.
**Status in Tests:** ✅ DEFINED - `app.cors.allowed-origin=http://localhost:4200` exists in [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L6)

**@Bean Methods:**
- [Line 41](apps/backend/src/main/java/com/lemarketjames/config/SecurityConfig.java#L41) - `securityFilterChain()`: Creates the security filter chain. Could throw `Exception`.
- [Line 67](apps/backend/src/main/java/com/lemarketjames/config/SecurityConfig.java#L67) - `corsConfigurationSource()`: Creates CORS configuration.

---

### MarketConfig
**File:** [apps/backend/src/main/java/com/lemarketjames/market/config/MarketConfig.java](apps/backend/src/main/java/com/lemarketjames/market/config/MarketConfig.java#L16)
**Line:** 16

**Configuration:**
```java
@Configuration
@EnableConfigurationProperties(MarketSimulationProperties.class)
public class MarketConfig {
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

**@Bean Methods:**
- [Line 24](apps/backend/src/main/java/com/lemarketjames/market/config/MarketConfig.java#L24) - `clock()`: Returns system UTC clock. Low failure risk.

---

### MarketSchedulingConfig
**File:** [apps/backend/src/main/java/com/lemarketjames/market/config/MarketSchedulingConfig.java](apps/backend/src/main/java/com/lemarketjames/market/config/MarketSchedulingConfig.java#L11)
**Line:** 11

**Configuration:**
```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sim", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketSchedulingConfig {
}
```

**Potential Issue:** The `@ConditionalOnProperty` checks if `sim.enabled` is true. However, since the condition has `matchIfMissing = true`, it will default to true if the property is not found. This configuration loads regardless during context initialization.

**Status in Tests:** ✅ EXPLICITLY DISABLED - `sim.enabled=false` in [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L24)

---

### MarketSimulationProperties
**File:** [apps/backend/src/main/java/com/lemarketjames/market/config/MarketSimulationProperties.java](apps/backend/src/main/java/com/lemarketjames/market/config/MarketSimulationProperties.java#L14)
**Line:** 14

**Configuration:**
```java
@Validated
@ConfigurationProperties(prefix = "sim")
public class MarketSimulationProperties {
    @Positive
    private long tickMs = 1000;
    
    @Positive
    private double speedMultiplier = 1.0;
    
    @Positive
    private long snapshotIntervalMs = 5000;
}
```

**Potential Issue:** The `@Validated` annotation triggers constraint validation:
- `tickMs` must be positive
- `speedMultiplier` must be positive  
- `snapshotIntervalMs` must be positive

If any property is set to zero or negative, validation fails during context initialization.

**Status in Tests:** ✅ ALL VALID
- `sim.tick-ms=1000` (positive) in [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L24)
- `sim.snapshot-interval-ms=5000` (positive) - default/property

---

## 2. @Component Classes with ApplicationRunner / CommandLineRunner

### MarketInitializer
**File:** [apps/backend/src/main/java/com/lemarketjames/market/service/MarketInitializer.java](apps/backend/src/main/java/com/lemarketjames/market/service/MarketInitializer.java#L15)
**Line:** 15

**Type:** `@Component` implementing `ApplicationRunner`

**Execution During Context Initialization:**
```java
@Component
public class MarketInitializer implements ApplicationRunner {
    public void run(ApplicationArguments args) {
        var instruments = persistence.loadInstruments();
        if (instruments.isEmpty()) {
            log.warn("No instruments have market simulation parameters; quotes will return 404. "
                    + "Has database/schema/006_market_simulation.sql been applied?");
        }
        simulator.load(instruments, persistence.loadStoredQuotes());
    }
}
```

**Critical Issue:** [Line 32-35](apps/backend/src/main/java/com/lemarketjames/market/service/MarketInitializer.java#L32-L35)
- Calls `persistence.loadInstruments()` - queries `instrument_market_params` table
- Calls `persistence.loadStoredQuotes()` - queries `market_quotes` table
- If these queries fail (missing tables, bad schema, database connection issues), context initialization FAILS

**Dependency Chain:**
- `MarketPersistenceService` - tries to load from database
- `MarketSimulator` - receives the loaded instruments

**Status in Tests:** ⚠️ CONDITIONAL
- Succeeds if `data.sql` loads correctly and creates `instrument_market_params` rows
- See [apps/backend/src/test/resources/data.sql](apps/backend/src/test/resources/data.sql) - inserts test instruments
- If `spring.jpa.defer-datasource-initialization` or `spring.sql.init.mode` are misconfigured, `data.sql` may not run before `MarketInitializer.run()` executes

**Risk Factor:** HIGH - This is the most likely failure point

---

### MarketScheduler
**File:** [apps/backend/src/main/java/com/lemarketjames/market/service/MarketScheduler.java](apps/backend/src/main/java/com/lemarketjames/market/service/MarketScheduler.java#L15)
**Line:** 15

**Type:** `@Component` with `@Scheduled` methods

**Configuration:**
```java
@Component
@ConditionalOnProperty(prefix = "sim", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketScheduler {
    @Scheduled(fixedRateString = "${sim.tick-ms:1000}")
    public void tick() { ... }
    
    @Scheduled(fixedRateString = "${sim.snapshot-interval-ms:5000}", initialDelayString = "...")
    public void saveSnapshot() { ... }
}
```

**Potential Issue:** 
- `@Scheduled` methods reference properties via `fixedRateString` and `initialDelayString`
- If property values are missing or invalid (non-numeric), scheduling fails
- However, ConditionalOnProperty prevents this bean from loading in tests since `sim.enabled=false`

**Status in Tests:** ✅ DISABLED - Not created because `sim.enabled=false`

---

## 3. @Service Classes with @Value Injection

### JwtService
**File:** [apps/backend/src/main/java/com/lemarketjames/auth/security/JwtService.java](apps/backend/src/main/java/com/lemarketjames/auth/security/JwtService.java#L17)
**Line:** 17

**Constructor Injection:**
```java
@Service
public class JwtService {
    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }
}
```

**Potential Issues:**
- [Line 29](apps/backend/src/main/java/com/lemarketjames/auth/security/JwtService.java#L29) Missing `jwt.secret` property
- [Line 30](apps/backend/src/main/java/com/lemarketjames/auth/security/JwtService.java#L30) Missing `jwt.expiration-ms` property
- `Keys.hmacShaKeyFor()` throws exception if secret is too short (must be 32+ bytes for HMAC-SHA256)

**Status in Tests:** ✅ VALID
- `jwt.secret=test-only-secret-key-not-used-in-production-32bytes+` (44 chars)
- `jwt.expiration-ms=3600000` in [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L3)

---

### SessionService
**File:** [apps/backend/src/main/java/com/lemarketjames/sessions/service/SessionService.java](apps/backend/src/main/java/com/lemarketjames/sessions/service/SessionService.java#L22)
**Line:** 22

**Constructor Injection:**
```java
@Service
public class SessionService {
    public SessionService(@Value("${jwt.secret}") String secret, AccountRepository accounts) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accounts = accounts;
    }
}
```

**Potential Issues:**
- [Line 28](apps/backend/src/main/java/com/lemarketjames/sessions/service/SessionService.java#L28) Same as JwtService - missing or invalid `jwt.secret`
- Identical HMAC-SHA256 key initialization risk

**Status in Tests:** ✅ VALID - Same property as JwtService

---

## 4. Database Schema & Initialization

### Test Data Loading
**File:** [apps/backend/src/test/resources/data.sql](apps/backend/src/test/resources/data.sql)

**Critical Configuration (test properties):**
```properties
spring.jpa.defer-datasource-initialization=true    # MUST be true
spring.sql.init.mode=always                         # MUST be "always"
spring.jpa.hibernate.ddl-auto=create-drop          # Creates tables, then runs data.sql
```

**Potential Failure Points:**

1. **Table Creation Failure**
   - If `@Entity` classes have mismatched mappings, Hibernate `create-drop` fails
   - Affected entities: `AccountEntity`, `ClientEntity`, `AddressEntity`, `InstrumentMarketParamsEntity`, `MarketQuoteEntity`, `PriceCandleEntity`, `Instrument`, `Order`, `HoldingsEntity`

2. **data.sql Execution Failure**
   - [Line 7-13](apps/backend/src/test/resources/data.sql#L7-L13) - INSERT into `instruments` table
   - [Line 15-27](apps/backend/src/test/resources/data.sql#L15-L27) - INSERT into `instrument_market_params` with JOIN to `instruments`
   - If `instruments` table doesn't exist or JOIN fails, data.sql fails

3. **Execution Order Issue**
   - `MarketInitializer.run()` is called AFTER schema creation but BEFORE or AFTER `data.sql`?
   - If called before `data.sql`, query for `instrument_market_params` returns empty (warns but doesn't fail)
   - If called after, instruments are loaded successfully

**Status in Tests:** ✅ PROPERLY CONFIGURED
- `spring.jpa.defer-datasource-initialization=true` ensures data.sql runs before ApplicationRunner beans
- `spring.sql.init.mode=always` forces data.sql execution

---

## 5. Repository Access During Context Initialization

### Potential Repository Query Failures

The following repositories are accessed during startup:

1. **MarketPersistenceService.loadInstruments()** [Line 51](apps/backend/src/main/java/com/lemarketjames/market/service/MarketPersistenceService.java#L51)
   - Queries: `instrument_market_params` JOIN `instruments`
   - Location: Called by `MarketInitializer.run()` (line 32)
   - Risk: HIGH if table doesn't exist

2. **MarketPersistenceService.loadStoredQuotes()** [Line 61](apps/backend/src/main/java/com/lemarketjames/market/service/MarketPersistenceService.java#L61)
   - Queries: `market_quotes` table
   - Location: Called by `MarketInitializer.run()` (line 34)
   - Risk: MEDIUM - table exists but may be empty (not an error)

---

## 6. Missing or Invalid Properties Analysis

| Property | Location | Required | Test Value | Risk |
|----------|----------|----------|-----------|------|
| `jwt.secret` | [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L3) | YES | `test-only-secret-key-not-used-in-production-32bytes+` | ✅ VALID |
| `jwt.expiration-ms` | [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L3) | YES | `3600000` | ✅ VALID |
| `app.cors.allowed-origin` | [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L6) | YES | `http://localhost:4200` | ✅ VALID |
| `spring.datasource.url` | [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L8) | YES | `jdbc:h2:mem:testdb;...` | ✅ VALID |
| `sim.enabled` | [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L24) | NO (default: true) | `false` | ✅ DISABLED |
| `sim.seed` | [apps/backend/src/test/resources/application.properties](apps/backend/src/test/resources/application.properties#L25) | NO (default: null) | `42` | ✅ VALID |

---

## 7. MOST LIKELY FAILURE SOURCES

### 🔴 **HIGH RISK - Primary Suspects**

1. **MarketInitializer Database Query Failure** [Line 32-34](apps/backend/src/main/java/com/lemarketjames/market/service/MarketInitializer.java#L32-L34)
   - Queries `instrument_market_params` and `market_quotes` before tables exist or after `data.sql` fails
   - Solution: Verify `spring.jpa.defer-datasource-initialization=true` and `spring.sql.init.mode=always` are set

2. **data.sql Execution Failure**
   - The SQL file's JOIN statement fails if `instruments` table isn't created first
   - Solution: Check Hibernate entity scan order, or add explicit table creation steps

3. **H2 Database Compatibility Issues**
   - Test uses H2 in PostgreSQL mode: `jdbc:h2:mem:testdb;MODE=PostgreSQL`
   - Some SQL or JDBC driver behavior may differ
   - Solution: Verify H2 driver version compatibility

### 🟡 **MEDIUM RISK - Less Likely**

4. **Missing or Invalid JWT Secret**
   - JwtService/SessionService initialization fails with malformed key
   - Current secret is 44 characters (valid for HMAC-SHA256)

5. **Entity Mapping Mismatches**
   - `@Entity` classes don't match database schema created by Hibernate
   - Solution: Check for missing `@Column` annotations or type mismatches

---

## Recommendations

1. **Check Test Logs:** Look for errors in `data.sql` execution, table creation failures, or schema validation errors
2. **Verify Ordering:** Ensure `spring.jpa.defer-datasource-initialization=true` is present (it is)
3. **Check `MarketInitializer`:** Add debug logging to see if queries execute and when
4. **Check H2 Version:** Verify H2 dependency in pom.xml supports the SQL in `data.sql`
5. **Validate Entities:** Run `mvn clean test` with verbose logging to see entity mapping errors
