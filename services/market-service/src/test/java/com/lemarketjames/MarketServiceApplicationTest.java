package com.lemarketjames;

import com.lemarketjames.market.MarketController;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.MarketSimulator;
import com.lemarketjames.market.service.MarketPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise production component scanning so shared client beans cannot break startup. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:market-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "sim.enabled=false"
})
class MarketServiceApplicationTest {
    @Autowired ApplicationContext context;
    // Instrument identity lives outside this service's JPA model; isolate that SQL boundary.
    @MockBean MarketPersistenceService persistence;

    @Test
    void startsWithLocalMarketDataProvider() {
        var providers = context.getBeansOfType(MarketDataService.class);
        assertEquals(1, providers.size());
        assertInstanceOf(MarketSimulator.class, providers.values().iterator().next());
        assertNotNull(context.getBean(MarketController.class));
    }
}
