package com.lemarketjames.watchlist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Watchlist endpoints from contract C6 (S41). They take no account id: the JWT says whose list it is. */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService watchlist;

    public WatchlistController(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("success", true, "watchlist", watchlist.list(username()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestBody WatchlistRequest request) {
        WatchlistItem item = watchlist.add(username(), request.symbol());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "item", item));
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> remove(@PathVariable String symbol) {
        watchlist.remove(username(), symbol);
        return ResponseEntity.noContent().build();
    }

    private static String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
