package com.lemarketjames.holdings.client;

import com.lemarketjames.common.security.InternalApiTokenFilter;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.execution.ExecutionSettings;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BR-02: trading-service answers {@code /internal/**} only to a holder of the shared token, so both
 * clients that call it must send it. A local HTTP server stands in for trading-service and records
 * the header each path received.
 */
class InternalTokenSentTest {

    static final String TOKEN = "test-token-sent";
    final Map<String, String> tokenByPath = new ConcurrentHashMap<>();
    HttpServer server;
    String baseUrl;

    @BeforeEach
    void startTradingServiceStandIn() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/", exchange -> {
            String sent = exchange.getRequestHeaders().getFirst(InternalApiTokenFilter.HEADER);
            tokenByPath.put(exchange.getRequestURI().getPath(), sent == null ? "<none>" : sent);
            byte[] body = "{\"rejectionReason\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void settlementSendsTheToken() {
        Order order = new Order(7, 5, Order.OrderType.BUY, BigDecimal.ONE);
        order.setOrderId(42);
        order.setPricePerUnit(new BigDecimal("101.0000"));

        new HoldingsSettlementClient(baseUrl, TOKEN, new ExecutionSettings()).settle(order);

        assertEquals(TOKEN, tokenByPath.get("/internal/holdings/settle"));
    }

    @Test
    void theSellCheckSendsTheToken() {
        new HoldingsValidationClient(baseUrl, TOKEN).validateSufficientHoldings(7, "alice", 5, BigDecimal.ONE);

        assertEquals(TOKEN, tokenByPath.get("/internal/holdings/validate"));
    }
}
