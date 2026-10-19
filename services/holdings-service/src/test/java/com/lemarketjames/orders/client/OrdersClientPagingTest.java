package com.lemarketjames.orders.client;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OrdersClient} against a real local HTTP server standing in for buy-sell-service, whose
 * account-orders endpoint is paged: every page is read, not just the first.
 */
class OrdersClientPagingTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsEveryPageUntilAShortOne() throws IOException {
        List<String> queries = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/orders/account/7", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            queries.add(query);
            // A full first page, then one more order.
            int count = query.contains("page=0") ? OrdersClient.PAGE_SIZE : 1;
            byte[] body = IntStream.range(0, count)
                    .mapToObj(i -> "{\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":1,\"orderStatus\":\"FILLED\"}")
                    .collect(Collectors.joining(",", "[", "]"))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "token")});
        OrdersClient client = new OrdersClient("http://localhost:" + server.getAddress().getPort(), request);

        assertEquals(OrdersClient.PAGE_SIZE + 1, client.getOrdersForAccount(7).size());
        assertEquals(List.of("page=0&size=100", "page=1&size=100"), queries);
    }
}
