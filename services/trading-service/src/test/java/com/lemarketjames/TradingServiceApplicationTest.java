package com.lemarketjames;

import com.lemarketjames.market.MarketControlController;
import com.lemarketjames.market.MarketController;
import com.lemarketjames.market.client.MarketDataClient;
import com.lemarketjames.market.client.PriceHistoryClient;
import com.lemarketjames.market.inprocess.InProcessMarketData;
import com.lemarketjames.market.inprocess.InProcessPriceHistory;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.PriceHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercise production component scanning, so the market's own prices are what every feature reads. */
@SpringBootTest
class TradingServiceApplicationTest {

    @Autowired ApplicationContext context;
    @Autowired MarketDataService marketData;
    @Autowired PriceHistoryService priceHistory;

    @Test
    void featuresReadPricesFromTheSimulatorInThisProcess() {
        assertInstanceOf(InProcessMarketData.class, marketData);
        assertInstanceOf(InProcessPriceHistory.class, priceHistory);
        assertNotNull(context.getBean(MarketController.class));
    }

    // market.service.url is unset here, so market-client's HTTP clients must not exist.
    @Test
    void marketClientHttpImplementationsAreOff() {
        assertTrue(context.getBeansOfType(MarketDataClient.class).isEmpty());
        assertTrue(context.getBeansOfType(PriceHistoryClient.class).isEmpty());
    }

    // The quote-feed test controls only exist where an environment turns them on (sim.control.enabled).
    @Test
    void feedControlsAreOffByDefault() {
        assertTrue(context.getBeansOfType(MarketControlController.class).isEmpty());
    }
}
