package com.lemarketjames.market.client;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PriceHistoryClient} against a real local HTTP server, as {@link MarketDataClientTest} does. */
class PriceHistoryClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void dailyBarsAreReadFromMarketService() throws IOException {
        AtomicReference<String> query = new AtomicReference<>();
        start(200, "[{\"date\":\"2026-10-14\",\"open\":1,\"high\":2,\"low\":0.5,\"close\":1.5,\"volume\":10}]", query);

        List<DailyBar> bars = client().dailyBars(7, LocalDate.of(2026, 9, 14));

        assertEquals("/api/market/history/7?from=2026-09-14", query.get());
        assertEquals(List.of(new DailyBar(LocalDate.of(2026, 10, 14), 1, 2, 0.5, 1.5, 10)), bars);
    }

    @Test
    void intradayPointsAreReadFromMarketService() throws IOException {
        start(200, "[{\"time\":\"2026-10-15T13:31:00Z\",\"price\":229.2}]", new AtomicReference<>());

        assertEquals(List.of(new PricePoint(Instant.parse("2026-10-15T13:31:00Z"), 229.2)), client().intraday(7));
    }

    @Test
    void aFailedCallIsAnEmptyHistory() throws IOException {
        start(503, "", new AtomicReference<>());

        assertTrue(client().dailyBars(7, LocalDate.of(2026, 9, 14)).isEmpty());
        assertTrue(client().intraday(7).isEmpty());
    }

    private PriceHistoryClient client() {
        return new PriceHistoryClient("http://localhost:" + server.getAddress().getPort(), 800, 800);
    }

    private void start(int status, String body, AtomicReference<String> query) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/market", exchange -> {
            query.set(exchange.getRequestURI().toString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }
}
