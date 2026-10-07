package com.lemarketjames.staffgateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises HTTP proxying, cookies, CORS and the explicit staff route boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaffGatewayTest {
    static final HttpServer upstream;
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            upstream.createContext("/", exchange -> {
                String body = exchange.getRequestURI() + "|" + exchange.getRequestHeaders().getFirst("Cookie")
                    + "|" + exchange.getRequestHeaders().getFirst("Origin");
                exchange.getResponseHeaders().add("Set-Cookie", "jwt=test; HttpOnly; Path=/");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            upstream.start();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        for (String service : new String[]{"auth", "buy-sell", "reporting"})
            registry.add("services." + service + "-url", () -> "http://localhost:" + upstream.getAddress().getPort());
    }
    @AfterAll static void stop() { upstream.stop(0); }
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> request(String method, String path, String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Origin", origin).header("Cookie", "jwt=staff-token")
            .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void forwardsStaffPathsCookiesAndQueryWithoutUpstreamOrigin() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/v1/orders/trades/search?orderId=42", "/api/v1/orders/trades/clients?name=Alex", "/api/v1/reports/period"}) {
            var response = request("GET", path, "http://localhost:4201");
            assertEquals(200, response.statusCode());
            assertEquals(path + "|jwt=staff-token|null", response.body());
            assertTrue(response.headers().firstValue("Set-Cookie").orElseThrow().contains("jwt=test"));
        }
        for (String path : new String[]{"/api/auth/login", "/api/auth/logout"})
            assertEquals(200, request("POST", path, "http://localhost:4201").statusCode());
    }
    @Test void excludesClientAndInternalRoutesAndWrongMethods() throws Exception {
        for (String path : new String[]{"/api/auth/register", "/api/v1/orders", "/internal/holdings/settle", "/api/v1/profile"})
            assertEquals(404, request("POST", path, "http://localhost:4201").statusCode());
        assertEquals(404, request("POST", "/api/v1/orders/trades/search", "http://localhost:4201").statusCode());
    }
    @Test void rejectsOtherOrigins() throws Exception {
        assertEquals(403, request("POST", "/api/auth/login", "http://untrusted.example").statusCode());
    }
    @Test void allowsStaffPreflight() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
            .header("Origin", "http://localhost:4201").header("Access-Control-Request-Method", "POST")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:4201", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }
}
