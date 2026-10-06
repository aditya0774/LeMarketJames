package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.submission.SubmissionRequestId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LMKT-99: every order submission leaves an audit trail, whether the order is saved or refused
 * (contract C2). Requests go through the real HTTP stack and commit; the trail is then read back
 * by the request ID the caller was given. No test transaction, so what is asserted is what was
 * committed. Runs with H2 by default and the real migrated PostgreSQL schema in Jenkins.
 */
@SpringBootTest(properties = "lmj.orders.restricted-locations=ZZ")
@AutoConfigureMockMvc
class OrderSubmissionAuditIntegrationTest {

    private static final List<String> COMMON_CHECKS_PASSED = List.of(
        "RULE_CHECKED:ACCOUNT_ACCESS:PASS", "RULE_CHECKED:ACCOUNT_STATUS:PASS",
        "RULE_CHECKED:LOCATION:PASS", "RULE_CHECKED:TRADABLE:PASS");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired InstrumentRepository instruments;
    @Autowired AuditEventRepository audit;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditTestCleanup cleanup;
    @MockBean MarketDataService market;
    // holdings-service is another process; its answer is all placement needs from it.
    @MockBean HoldingsValidationClient holdings;
    String username;
    Integer clientId, accountId, instrumentId, nonTradableId;
    Cookie cookie;

    @BeforeEach
    void registerAndLogin() {
        username = "aud" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accountId = register(username);
        clientId = clients.findByUsername(username).orElseThrow().getClientId();
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(username));
        quoted(Instant.now());
    }

    @AfterEach
    void removeOnlyThisTestsCommittedData() {
        cleanup.removeClient(username);
        if (nonTradableId != null) {
            jdbc.update("DELETE FROM instruments WHERE instrument_id=?", nonTradableId);
        }
    }

    // ---- AC1: SUBMITTED carries the client, the order and the server's UTC time ----

    @Test
    void anAcceptedBuyIsAuditedWithItsClientOrderAndServerTime() throws Exception {
        Instant before = Instant.now();
        MvcResult result = submit("BUY", accountId, instrumentId, 1).andExpect(status().isCreated()).andReturn();
        Instant after = Instant.now();
        int orderId = json.readTree(result.getResponse().getContentAsString()).get("orderId").asInt();

        List<AuditEventEntity> trail = trailOf(result);

        assertEquals(steps("SUBMITTED", COMMON_CHECKS_PASSED, "RULE_CHECKED:PRICE_AVAILABLE:PASS",
            "RULE_CHECKED:QUOTE_FRESH:PASS", "RULE_CHECKED:CASH:PASS", "VALIDATED"), stepsOf(trail));
        AuditEventEntity submitted = trail.get(0);
        assertEquals(AuditEventType.SUBMITTED, submitted.getEventType());
        assertEquals(clientId, submitted.getClientId());
        assertEquals(orderId, submitted.getOrderId());
        assertEquals(accountId, submitted.getAccountId());
        // The time is the server's own clock, stored as an instant (UTC), not anything the caller sent.
        assertFalse(submitted.getOccurredAt().isBefore(before.minusMillis(1)), "not before the request");
        assertFalse(submitted.getOccurredAt().isAfter(after.plusMillis(1)), "not after the response");
        assertEquals("BUY", submitted.getDetails().get("side"));
        assertEquals(instrumentId, ((Number) submitted.getDetails().get("instrumentId")).intValue());
        assertEquals(0, new BigDecimal("250.1235").compareTo(new BigDecimal(submitted.getDetails().get("price").toString())));
        // Every event of the submission belongs to the same order, client and request.
        trail.forEach(event -> {
            assertEquals(orderId, event.getOrderId());
            assertEquals(clientId, event.getClientId());
            assertEquals(accountId, event.getAccountId());
        });
        // Written with the order: the order the trail points to is the one that was committed.
        assertEquals(1, countOrders());
    }

    // ---- AC2: one event per check that ran, with its rule and result ----

    @Test
    void anAcceptedSellIsAuditedWithTheHoldingsRuleAndTheChecksThatRan() throws Exception {
        MvcResult result = submit("SELL", accountId, instrumentId, 3).andExpect(status().isCreated()).andReturn();

        List<AuditEventEntity> trail = trailOf(result);

        assertEquals(steps("SUBMITTED", COMMON_CHECKS_PASSED, "RULE_CHECKED:HOLDINGS:PASS", "VALIDATED"), stepsOf(trail));
        assertEquals(List.of("ACCOUNT_ACCESS", "ACCOUNT_STATUS", "LOCATION", "TRADABLE", "HOLDINGS"),
            trail.get(trail.size() - 1).getDetails().get("checks"));
        assertNull(trail.get(0).getDetails().get("price"), "a SELL isn't priced at placement");
    }

    @Test
    void aBuyRefusedForCashLeavesItsTrailAndNoOrder() throws Exception {
        MvcResult result = submit("BUY", accountId, instrumentId, 10000)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH")).andReturn();

        List<AuditEventEntity> trail = assertRefusedTrail(result, steps("SUBMITTED", COMMON_CHECKS_PASSED,
            "RULE_CHECKED:PRICE_AVAILABLE:PASS", "RULE_CHECKED:QUOTE_FRESH:PASS",
            "RULE_CHECKED:CASH:FAIL:INSUFFICIENT_CASH"));
        // The price the order was valued at when it was refused is kept with the submission.
        assertEquals(0, new BigDecimal("250.1235").compareTo(new BigDecimal(trail.get(0).getDetails().get("price").toString())));
    }

    @Test
    void aBuyRefusedForNoPriceLeavesItsTrailAndNoOrder() throws Exception {
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.empty());

        MvcResult result = submit("BUY", accountId, instrumentId, 1)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRICE_UNAVAILABLE")).andReturn();

        assertRefusedTrail(result, steps("SUBMITTED", COMMON_CHECKS_PASSED,
            "RULE_CHECKED:PRICE_AVAILABLE:FAIL:PRICE_UNAVAILABLE"));
    }

    @Test
    void aBuyRefusedForAStaleQuoteLeavesItsTrailAndNoOrder() throws Exception {
        quoted(Instant.now().minus(Duration.ofHours(1)));

        MvcResult result = submit("BUY", accountId, instrumentId, 1)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STALE_QUOTE")).andReturn();

        assertRefusedTrail(result, steps("SUBMITTED", COMMON_CHECKS_PASSED,
            "RULE_CHECKED:PRICE_AVAILABLE:PASS", "RULE_CHECKED:QUOTE_FRESH:FAIL:STALE_QUOTE"));
    }

    @Test
    void aSellRefusedForHoldingsLeavesItsTrailAndNoOrder() throws Exception {
        doThrow(new InsufficientHoldingsException("Insufficient holdings"))
            .when(holdings).validateSufficientHoldings(eq(accountId), eq(username), eq(instrumentId), any());

        // Refused by an exception handler, not a returned response: the trail and the ID still get out.
        MvcResult result = submit("SELL", accountId, instrumentId, 4)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_HOLDINGS")).andReturn();

        assertRefusedTrail(result, steps("SUBMITTED", COMMON_CHECKS_PASSED,
            "RULE_CHECKED:HOLDINGS:FAIL:INSUFFICIENT_HOLDINGS"));
    }

    @Test
    void anOrderForASuspendedOrUnknownInstrumentLeavesItsTrailAndNoOrder() throws Exception {
        nonTradableId = saveNonTradableInstrument();
        List<String> refusedAtTradable = List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:PASS",
            "RULE_CHECKED:ACCOUNT_STATUS:PASS", "RULE_CHECKED:LOCATION:PASS", "RULE_CHECKED:TRADABLE:FAIL:NOT_TRADABLE");

        MvcResult suspended = submit("BUY", accountId, nonTradableId, 1)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_TRADABLE")).andReturn();
        MvcResult unknown = submit("BUY", accountId, Integer.MAX_VALUE, 1)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists()).andReturn();

        assertRefusedTrail(suspended, refusedAtTradable);
        assertRefusedTrail(unknown, refusedAtTradable);
        // Two submissions, two requests: each has its own ID and its own trail.
        assertNotEquals(requestIdOf(suspended), requestIdOf(unknown));
    }

    @Test
    void anOrderFromAnAccountThatMayNotTradeLeavesItsTrailAndNoOrder() throws Exception {
        AccountEntity account = accounts.findById(accountId).orElseThrow();
        account.setTradingEnabled(false);
        accounts.saveAndFlush(account);

        MvcResult result = submit("BUY", accountId, instrumentId, 1)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED")).andReturn();

        assertRefusedTrail(result, List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:PASS",
            "RULE_CHECKED:ACCOUNT_STATUS:FAIL:ACCOUNT_RESTRICTED"));
    }

    @Test
    void anOrderFromARestrictedLocationLeavesItsTrailAndNoOrder() throws Exception {
        jdbc.update("UPDATE addresses SET country='ZZ' WHERE client_id=?", clientId);

        MvcResult result = submit("SELL", accountId, instrumentId, 1)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOCATION_RESTRICTED")).andReturn();

        assertRefusedTrail(result, List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:PASS",
            "RULE_CHECKED:ACCOUNT_STATUS:PASS", "RULE_CHECKED:LOCATION:FAIL:LOCATION_RESTRICTED"));
    }

    @Test
    void anOrderForSomeoneElsesAccountIsAuditedAgainstTheCallerOnly() throws Exception {
        MvcResult result = submit("BUY", Integer.MAX_VALUE, instrumentId, 1)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED")).andReturn();

        List<AuditEventEntity> trail = trailOf(result);

        assertEquals(List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:FAIL:ACCOUNT_ACCESS_DENIED"), stepsOf(trail));
        trail.forEach(event -> {
            assertNull(event.getOrderId());
            assertEquals(clientId, event.getClientId());
            assertNull(event.getAccountId(), "the account isn't the caller's, so the event doesn't claim it");
        });
        assertEquals(Integer.MAX_VALUE, ((Number) trail.get(0).getDetails().get("requestedAccountId")).intValue());
    }

    @Test
    void aCheckThatCannotCompleteStillLeavesItsTrail() {
        doThrow(new IllegalStateException("holdings-service unreachable"))
            .when(holdings).validateSufficientHoldings(eq(accountId), eq(username), eq(instrumentId), any());

        // Nothing maps this failure to a response, so it surfaces from the request itself.
        assertThrows(Exception.class, () -> submit("SELL", accountId, instrumentId, 1));

        List<AuditEventEntity> trail = audit.findByRequestIdOrderByAuditIdAsc(onlyRequestIdOfTheCaller());
        assertEquals(steps("SUBMITTED", COMMON_CHECKS_PASSED, "RULE_CHECKED:HOLDINGS:ERROR"), stepsOf(trail));
        assertEquals(0, countOrders());
    }

    // ---- Requests that never reach placement are not submissions ----

    @Test
    void malformedAndUnauthenticatedRequestsAreNotSubmissions() throws Exception {
        MvcResult malformed = submit("BUY", accountId, instrumentId, 0)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.quantity").exists()).andReturn();
        MvcResult anonymous = mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
            .content(body("BUY", accountId, instrumentId, 1))).andExpect(status().isUnauthorized()).andReturn();

        assertNull(requestIdOf(malformed));
        assertNull(requestIdOf(anonymous));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE client_id=? OR account_id=?",
            Integer.class, clientId, accountId));
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions submit(String side, int account, int instrument,
                                                                       int quantity) throws Exception {
        return mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(body(side, account, instrument, quantity)));
    }

    private String body(String side, int account, int instrument, int quantity) throws Exception {
        return json.writeValueAsString(Map.of("accountId", account, "instrumentId", instrument,
            "orderType", side, "quantity", quantity));
    }

    private void quoted(Instant quoteTime) {
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.of(
            new QuoteSnapshot(null, 250.12345, 250.12345, 250.12345, 250.12345,
                250.12345, 250.12345, 250.12345, 0, quoteTime, null)));
    }

    private static String requestIdOf(MvcResult result) {
        return result.getResponse().getHeader(SubmissionRequestId.HEADER);
    }

    /** The trail filed under the ID the caller was given, in the order it was written. */
    private List<AuditEventEntity> trailOf(MvcResult result) {
        String requestId = requestIdOf(result);
        assertNotNull(requestId, "every submission response carries its request ID");
        List<AuditEventEntity> trail = audit.findByRequestIdOrderByAuditIdAsc(requestId);
        trail.forEach(event -> assertEquals(requestId, event.getRequestId()));
        return trail;
    }

    /** A refused order: no order row, and a trail with no order ID that ends on the check that stopped it. */
    private List<AuditEventEntity> assertRefusedTrail(MvcResult result, List<String> expectedSteps) {
        List<AuditEventEntity> trail = trailOf(result);
        assertEquals(expectedSteps, stepsOf(trail));
        trail.forEach(event -> {
            assertNull(event.getOrderId(), "a refused order has no order ID");
            assertEquals(clientId, event.getClientId());
            assertEquals(accountId, event.getAccountId());
        });
        assertEquals(0, countOrders(), "a refused order saves no order");
        return trail;
    }

    /** Each event as TYPE, or TYPE:RULE:RESULT[:reason] for a check. */
    private static List<String> stepsOf(List<AuditEventEntity> trail) {
        return trail.stream().map(event -> {
            Map<String, Object> details = event.getDetails();
            if (event.getEventType() != AuditEventType.RULE_CHECKED) {
                return event.getEventType().name();
            }
            return "RULE_CHECKED:" + details.get("rule") + ":" + details.get("result")
                + (details.containsKey("reason") ? ":" + details.get("reason") : "");
        }).toList();
    }

    private static List<String> steps(String first, List<String> middle, String... rest) {
        List<String> all = new java.util.ArrayList<>(List.of(first));
        all.addAll(middle);
        all.addAll(List.of(rest));
        return all;
    }

    private int countOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE account_id=?", Integer.class, accountId);
    }

    private String onlyRequestIdOfTheCaller() {
        return jdbc.queryForObject("SELECT DISTINCT request_id FROM audit_log WHERE client_id=?", String.class, clientId);
    }

    private Integer saveNonTradableInstrument() {
        Instrument instrument = new Instrument();
        instrument.setTicker("NT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        instrument.setName("Non-tradable test fixture");
        instrument.setAssetClass(Instrument.AssetClass.EQUITY);
        instrument.setCurrency("USD");
        instrument.setTradable(false);
        return instruments.saveAndFlush(instrument).getInstrumentId();
    }

    // Auth is a separate service; seed its shared account data and use its JWT format.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-buy-sell-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Audit Test");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("test-only");
        client.setEmploymentStatus("EMPLOYED");
        client.setInvestmentExperience("beginner");
        client.setAccountStatus(AccountStatus.ACTIVE);
        client = clients.saveAndFlush(client);
        AddressEntity address = new AddressEntity();
        address.setClientId(client.getClientId());
        address.setAddressType("RESIDENTIAL");
        address.setStreetAddress("123 Main St");
        address.setCity("Boston");
        address.setState("MA");
        address.setPostalCode("02110");
        address.setCountry("US");
        addresses.saveAndFlush(address);
        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(new BigDecimal("500"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }
}
