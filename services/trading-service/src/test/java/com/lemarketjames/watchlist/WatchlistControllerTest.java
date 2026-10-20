package com.lemarketjames.watchlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The C6 watchlist shapes and status codes (S41, LMKT-131). */
@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    private static final Instant ADDED = Instant.parse("2026-10-20T14:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockBean private WatchlistService watchlist;

    @Test
    @WithMockUser(username = "alice")
    void listReturnsTheCallersStocksInTheContractShape() throws Exception {
        when(watchlist.list("alice")).thenReturn(List.of(
                new WatchlistItem("NVDA", ADDED), new WatchlistItem("AAPL", ADDED.minusSeconds(60))));

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.watchlist.length()").value(2))
                .andExpect(jsonPath("$.watchlist[0].symbol").value("NVDA"))
                .andExpect(jsonPath("$.watchlist[0].addedAt").value("2026-10-20T14:00:00Z"))
                .andExpect(jsonPath("$.watchlist[1].symbol").value("AAPL"));
    }

    @Test
    @WithMockUser(username = "alice")
    void anEmptyListIsOkWithAnEmptyArray() throws Exception {
        when(watchlist.list("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.watchlist").isEmpty());
    }

    @Test
    @WithMockUser(username = "alice")
    void addReturnsCreatedWithTheItem() throws Exception {
        when(watchlist.add("alice", "AAPL")).thenReturn(new WatchlistItem("AAPL", ADDED));

        mockMvc.perform(post("/api/v1/watchlist").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.item.symbol").value("AAPL"))
                .andExpect(jsonPath("$.item.addedAt").value("2026-10-20T14:00:00Z"));
    }

    @Test
    @WithMockUser(username = "alice")
    void addingAStockAlreadyWatchedIsConflict() throws Exception {
        when(watchlist.add("alice", "AAPL")).thenThrow(new WatchlistException(
                WatchlistService.ALREADY_WATCHED, "AAPL is already on your watchlist"));

        mockMvc.perform(post("/api/v1/watchlist").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ALREADY_WATCHED"))
                .andExpect(jsonPath("$.error").value("AAPL is already on your watchlist"));
    }

    @Test
    @WithMockUser(username = "alice")
    void addingAnUnknownSymbolIsNotFoundWithTheContractError() throws Exception {
        when(watchlist.add("alice", "NOPE")).thenThrow(new WatchlistException(
                WatchlistService.SYMBOL_NOT_FOUND, "Symbol not found"));

        mockMvc.perform(post("/api/v1/watchlist").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"NOPE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Symbol not found"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice")
    void removeIsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlist/AAPL").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(watchlist).remove("alice", "AAPL");
    }

    @Test
    @WithMockUser(username = "ops")
    void staffWithoutAnAccountGetNotFound() throws Exception {
        when(watchlist.list("ops")).thenThrow(new WatchlistException(
                WatchlistService.ACCOUNT_NOT_FOUND, "Account not found"));
        doThrow(new WatchlistException(WatchlistService.ACCOUNT_NOT_FOUND, "Account not found"))
                .when(watchlist).remove("ops", "AAPL");

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("Account not found"));
        mockMvc.perform(delete("/api/v1/watchlist/AAPL").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/watchlist").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\"}"))
                .andExpect(status().isUnauthorized());
    }
}
