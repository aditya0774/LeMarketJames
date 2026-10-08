package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.messaging.OrderEventForwarder;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LMKT-137: an order's events reach Kafka. A BUY is placed, accepted and filled through the API with
 * the real transitions, forwarder and Kafka publisher, and the records are then read back from the
 * broker with a separate consumer. Only the two remote services (quote feed, settlement) are mocked.
 *
 * <p>Needs a broker, so it runs only with the {@code kafka-test} profile, given the way Jenkins
 * gives it: {@code -Dspring.profiles.active=kafka-test} (see application-kafka-test.properties).
 * Without it the test is skipped rather than failed, and the stub publisher stays in use. Market
 * hours are switched off so the fill doesn't depend on when the build runs.
 */
@SpringBootTest(properties = "lmj.execution.respect-market-hours=false")
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*kafka-test.*")
class OrderEventKafkaIntegrationTest {
    /** Generous, for a broker that has only just started; a record normally arrives within a second. */
    static final Duration ARRIVAL_TIMEOUT = Duration.ofSeconds(30);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired InstrumentRepository instruments;
    @Autowired JwtService jwt;
    @Autowired AuditTestCleanup cleanup;
    @MockBean MarketDataService market;
    @MockBean HoldingsSettlementClient settlement;
    @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers;
    KafkaConsumer<String, String> consumer;
    String username;
    Integer accountId;
    Integer instrumentId;

    @BeforeEach
    void registerClientAndStartListening() {
        username = "kafka" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accountId = register(username);
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        // One fresh quote serves placement and the fill.
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.of(
            new QuoteSnapshot(null, 101.00, 100.76543, 101.23456, 100, 102, 99, 100, 0, Instant.now(), null)));

        // A consumer of its own, as another service would have. It starts at the end of every topic,
        // so it sees only what this test publishes, whatever earlier runs left on the broker.
        consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        List<TopicPartition> partitions = new ArrayList<>();
        for (String topic : List.of(OrderEventForwarder.SUBMITTED_TOPIC, OrderEventForwarder.STATUS_CHANGED_TOPIC,
                OrderEventForwarder.FILLED_TOPIC)) {
            // The service created the topics when it started (OrderEventTopics).
            consumer.partitionsFor(topic).forEach(part -> partitions.add(new TopicPartition(topic, part.partition())));
        }
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        // seekToEnd is lazy; asking for the position pins it before the order is placed.
        partitions.forEach(consumer::position);
    }

    @AfterEach
    void stopListeningAndRemoveOnlyThisTestsData() {
        consumer.close();
        cleanup.removeClient(username);
    }

    @Test
    void anOrdersEventsArriveOnTheirTopicsKeyedByOrderId() throws Exception {
        Cookie client = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(username));
        String placed = mvc.perform(post("/api/v1/orders").cookie(client).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("accountId", accountId, "instrumentId", instrumentId,
                    "orderType", "BUY", "quantity", 2))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int orderId = json.readTree(placed).get("orderId").asInt();
        for (String next : List.of("ACCEPTED", "FILLED")) {
            mvc.perform(put("/api/v1/orders/" + orderId + "/status/" + next).with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk());
        }

        List<ConsumerRecord<String, String>> arrived = arrivalsFor(orderId);

        assertPlacementWasAnnouncedOnce(orderId, on(OrderEventForwarder.SUBMITTED_TOPIC, arrived));

        // Every transition from SUBMITTED to FILLED is there, whichever statuses execution passes
        // through on the way (contract C1); placement itself isn't a transition (contract C6).
        List<ConsumerRecord<String, String>> statusChanges = on(OrderEventForwarder.STATUS_CHANGED_TOPIC, arrived);
        assertTrue(statusChanges.size() >= 2, "status changes on " + OrderEventForwarder.STATUS_CHANGED_TOPIC);
        String status = "SUBMITTED";
        for (ConsumerRecord<String, String> change : statusChanges) {
            // Same key, so the same partition, so they arrive in the order they happened:
            // each change starts from the status the one before it ended in.
            assertEquals(statusChanges.get(0).partition(), change.partition());
            JsonNode event = json.readTree(change.value());
            assertEquals(orderId, event.get("orderId").asInt());
            assertEquals(accountId, event.get("accountId").asInt());
            assertEquals(status, event.get("from").asText());
            // Times are ISO-8601 text, as in the REST API.
            assertNotNull(Instant.parse(event.get("occurredAt").asText()));
            status = event.get("to").asText();
        }
        assertEquals("FILLED", status);
        assertTrue(statusChanges.stream().anyMatch(change -> change.value().contains("\"to\":\"ACCEPTED\"")));

        List<ConsumerRecord<String, String>> fills = on(OrderEventForwarder.FILLED_TOPIC, arrived);
        assertEquals(1, fills.size(), "fills on " + OrderEventForwarder.FILLED_TOPIC);
        JsonNode fill = json.readTree(fills.get(0).value());
        assertEquals(orderId, fill.get("orderId").asInt());
        assertEquals(accountId, fill.get("accountId").asInt());
        assertEquals(instrumentId, fill.get("instrumentId").asInt());
        assertEquals("BUY", fill.get("side").asText());
        assertEquals(0, new BigDecimal("2").compareTo(fill.get("quantity").decimalValue()));
        // A BUY fills at the ask, rounded to four decimals.
        assertEquals(0, new BigDecimal("101.2346").compareTo(fill.get("price").decimalValue()));
        assertFalse(fill.get("quoteSource").asText().isEmpty());
        assertNotNull(Instant.parse(fill.get("filledAt").asText()));
        assertNotNull(Instant.parse(fill.get("quoteTime").asText()));
    }

    /** The order's first event: one record on the submitted topic, saying what was ordered. */
    private void assertPlacementWasAnnouncedOnce(int orderId, List<ConsumerRecord<String, String>> submissions)
            throws Exception {
        assertEquals(1, submissions.size(), "submissions on " + OrderEventForwarder.SUBMITTED_TOPIC);
        JsonNode submission = json.readTree(submissions.get(0).value());
        assertEquals(orderId, submission.get("orderId").asInt());
        assertEquals(accountId, submission.get("accountId").asInt());
        assertEquals(instrumentId, submission.get("instrumentId").asInt());
        assertEquals("BUY", submission.get("side").asText());
        assertEquals(0, new BigDecimal("2").compareTo(submission.get("quantity").decimalValue()));
        // A BUY is placed at the ask, rounded to four decimals.
        assertEquals(0, new BigDecimal("101.2346").compareTo(submission.get("price").decimalValue()));
        assertNotNull(Instant.parse(submission.get("submittedAt").asText()));
    }

    /**
     * Reads until the order's submission and its last events have arrived, the fill and the change
     * to FILLED, or the timeout passes. The topics are read independently, so each is waited for; the
     * earlier status changes sit before the last one in the same partition. Other orders' records are
     * ignored.
     */
    private List<ConsumerRecord<String, String>> arrivalsFor(int orderId) {
        List<ConsumerRecord<String, String>> arrived = new ArrayList<>();
        boolean submitted = false;
        boolean filled = false;
        boolean changedToFilled = false;
        long deadline = System.nanoTime() + ARRIVAL_TIMEOUT.toNanos();
        while (!(submitted && filled && changedToFilled) && System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> received : consumer.poll(Duration.ofMillis(250))) {
                if (!String.valueOf(orderId).equals(received.key())) continue;
                arrived.add(received);
                if (received.topic().equals(OrderEventForwarder.SUBMITTED_TOPIC)) submitted = true;
                else if (received.topic().equals(OrderEventForwarder.FILLED_TOPIC)) filled = true;
                else if (received.value().contains("\"to\":\"FILLED\"")) changedToFilled = true;
            }
        }
        return arrived;
    }

    private static List<ConsumerRecord<String, String>> on(String topic, List<ConsumerRecord<String, String>> arrived) {
        return arrived.stream().filter(received -> received.topic().equals(topic)).toList();
    }

    // Auth is a separate service; seed its shared account data and use its JWT format.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-buy-sell-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Kafka Test");
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
        account.setCashBalance(new BigDecimal("10000"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }
}
