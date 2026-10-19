package com.lemarketjames.holdings;

import com.lemarketjames.holdings.service.SettlementFaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test control for settlement: make the settlements of one account fail part-way, and let them
 * work again, so failure handling can be tested on a running stack.
 *
 * <p>Only exists when {@code lmj.settlement.control-enabled=true} (test environments), and lives
 * under /internal, which no web app routes: like the settlement endpoint itself, it is reachable
 * only by calling trading-service directly on its own port.
 */
@RestController
@RequestMapping("/internal/holdings/control/settlement-faults/{accountId}")
@ConditionalOnProperty(prefix = "lmj.settlement", name = "control-enabled", havingValue = "true")
public class SettlementControlController {

    private final SettlementFaults faults;

    public SettlementControlController(SettlementFaults faults) {
        this.faults = faults;
    }

    @GetMapping
    public FaultState state(@PathVariable int accountId) {
        return new FaultState(accountId, faults.failsFor(accountId), faults.failuresOf(accountId));
    }

    /** From now on every settlement for the account fails after its writes, and is rolled back. */
    @PutMapping
    public FaultState fail(@PathVariable int accountId) {
        faults.failFor(accountId);
        return state(accountId);
    }

    /** Settlements for the account work again. */
    @DeleteMapping
    public FaultState restore(@PathVariable int accountId) {
        faults.restore(accountId);
        return state(accountId);
    }

    /**
     * @param accountId the account asked about
     * @param failing   whether its settlements fail at the moment
     * @param failures  how many of them have failed since that was switched on; 0 when it is off
     */
    public record FaultState(int accountId, boolean failing, int failures) {
    }
}
