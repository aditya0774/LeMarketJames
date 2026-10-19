package com.lemarketjames.trades;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import com.lemarketjames.trades.dto.TradeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final OrdersClient ordersClient;
    private final MarketDataService marketData;
    private final AccountRepository accountRepository;

    public TradeService(OrdersClient ordersClient, MarketDataService marketData, AccountRepository accountRepository) {
        this.ordersClient = ordersClient;
        this.marketData = marketData;
        this.accountRepository = accountRepository;
    }

    public List<TradeDto> getTradeHistory(Integer accountId, String username) {
        validateAccountOwnership(accountId, username);

        return ordersClient.getOrdersForAccount(accountId).stream()
                .filter(OrderSummary::isFilled)
                .map(this::toDto)
                .toList();
    }

    private TradeDto toDto(OrderSummary order) {
        String symbol = marketData.findByInstrumentId(order.instrumentId())
                .map(q -> q.instrument().ticker())
                .orElse(null);
        return new TradeDto(symbol, order.orderType(), order.quantity(), order.pricePerUnit(), order.filledAt());
    }

    private void validateAccountOwnership(Integer accountId, String username) {
        if (!accountRepository.existsByAccountIdAndUsername(accountId, username)) {
            log.warn("Trade history access denied for username={} accountId={}", username, accountId);
            throw new UnauthorizedException("User is not authorized to access this account");
        }
    }
}
