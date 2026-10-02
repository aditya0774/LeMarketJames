package com.lemarketjames.market.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link MarketDataClient} against real local HTTP servers (JDK's built-in
 * {@link HttpServer}, no extra test dependency) rather than mocks, so the configured
 * connect/read timeout and its effect on {@link MarketFeedStatus} are genuinely proven rather
 * than assumed.
 */
class MarketDataClientTest {

    private static final long SHORT_TIMEOUT_MS = 800;

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
        servers.clear();
    }

    @Test
    void readTimeoutIsDetectedAndThenTheFeedAutoResumes() throws IOException {
        MarketFeedStatus status = new MarketFeedStatus();

        HttpServer hungServer = httpServer(exchange -> {
            try {
                // Sleeps well past the client's read timeout, simulating a hung market-service.
                Thread.sleep(SHORT_TIMEOUT_MS * 5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, "[]");
        });
        MarketDataClient hungClient = clientFor(hungServer, status);

        assertTrue(hungClient.findAll().isEmpty());
        assertFalse(status.isAvailable(), "a hung call should be detected as a feed outage");
        assertTrue(status.outageStartedAt().isPresent());

        HttpServer respondingServer = httpServer(exchange -> respond(exchange, "[]"));
        MarketDataClient respondingClient = clientFor(respondingServer, status);

        assertTrue(respondingClient.findAll().isEmpty());
        assertTrue(status.isAvailable(), "a successful call should resume the feed automatically");
        assertTrue(status.outageStartedAt().isEmpty());
    }

    @Test
    void successfulCallsNeverFlagAnOutage() throws IOException {
        MarketFeedStatus status = new MarketFeedStatus();
        HttpServer server = httpServer(exchange -> respond(exchange, "[]"));

        MarketDataClient client = clientFor(server, status);
        client.findAll();

        assertTrue(status.isAvailable());
    }

    private MarketDataClient clientFor(HttpServer server, MarketFeedStatus status) {
        String url = "http://localhost:" + server.getAddress().getPort();
        return new MarketDataClient(url, SHORT_TIMEOUT_MS, SHORT_TIMEOUT_MS, status);
    }

    private HttpServer httpServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/market/quotes", handler);
        server.start();
        servers.add(server);
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
