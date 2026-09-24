package com.lemarketjames.holdings.service;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.dto.SettlementRequest;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingsSettlementServiceTest {

    @Mock
    private HoldingsRepository holdingsRepository;

    @Mock
    private AccountRepository accountRepository;

    private HoldingsSettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new HoldingsSettlementService(holdingsRepository, accountRepository);
    }

    @Test
    void buySettlesNewHoldingAndDebitsCash() {
        AccountEntity account = accountWithBalance(new BigDecimal("5000.00"));
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));
        when(holdingsRepository.findByAccountIdAndInstrumentId(1, 10)).thenReturn(Optional.empty());

        settlementService.settle(request(1, 10, SettlementRequest.OrderType.BUY,
                new BigDecimal("10"), new BigDecimal("100.00")));

        assertEquals(new BigDecimal("4000.00"), account.getCashBalance());
        ArgumentCaptor<HoldingsEntity> captor = ArgumentCaptor.forClass(HoldingsEntity.class);
        verify(holdingsRepository).save(captor.capture());
        assertEquals(new BigDecimal("10"), captor.getValue().getQuantity());
        assertEquals(new BigDecimal("100.00"), captor.getValue().getAverageCost());
    }

    @Test
    void buyIntoExistingHoldingComputesWeightedAverageCost() {
        AccountEntity account = accountWithBalance(new BigDecimal("5000.00"));
        HoldingsEntity existing = new HoldingsEntity(1, 10, new BigDecimal("10"), new BigDecimal("100.00"));

        when(accountRepository.findById(1)).thenReturn(Optional.of(account));
        when(holdingsRepository.findByAccountIdAndInstrumentId(1, 10)).thenReturn(Optional.of(existing));

        // Buy 10 more at 200 -> (10*100 + 10*200) / 20 = 150
        settlementService.settle(request(1, 10, SettlementRequest.OrderType.BUY,
                new BigDecimal("10"), new BigDecimal("200.00")));

        assertEquals(new BigDecimal("20"), existing.getQuantity());
        assertEquals(0, new BigDecimal("150.0000").compareTo(existing.getAverageCost()));
        verify(holdingsRepository).save(existing);
    }

    @Test
    void buyRejectedWhenCashInsufficient() {
        AccountEntity account = accountWithBalance(new BigDecimal("50.00"));
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class, () -> settlementService.settle(
                request(1, 10, SettlementRequest.OrderType.BUY, new BigDecimal("10"), new BigDecimal("100.00"))));

        verify(holdingsRepository, never()).save(any());
        assertEquals(new BigDecimal("50.00"), account.getCashBalance());
    }

    @Test
    void sellCreditsCashAndReducesQuantity() {
        AccountEntity account = accountWithBalance(new BigDecimal("1000.00"));
        HoldingsEntity existing = new HoldingsEntity(1, 10, new BigDecimal("10"), new BigDecimal("100.00"));

        when(holdingsRepository.findByAccountIdAndInstrumentId(1, 10)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));

        settlementService.settle(request(1, 10, SettlementRequest.OrderType.SELL,
                new BigDecimal("4"), new BigDecimal("120.00")));

        assertEquals(new BigDecimal("6"), existing.getQuantity());
        assertEquals(new BigDecimal("1480.00"), account.getCashBalance());
        verify(holdingsRepository).save(existing);
        verify(holdingsRepository, never()).delete(any());
    }

    @Test
    void sellingEntirePositionDeletesTheHolding() {
        AccountEntity account = accountWithBalance(new BigDecimal("1000.00"));
        HoldingsEntity existing = new HoldingsEntity(1, 10, new BigDecimal("10"), new BigDecimal("100.00"));

        when(holdingsRepository.findByAccountIdAndInstrumentId(1, 10)).thenReturn(Optional.of(existing));
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));

        settlementService.settle(request(1, 10, SettlementRequest.OrderType.SELL,
                new BigDecimal("10"), new BigDecimal("120.00")));

        verify(holdingsRepository).delete(existing);
        verify(holdingsRepository, never()).save(any());
    }

    @Test
    void sellRejectedWhenHoldingMissing() {
        when(holdingsRepository.findByAccountIdAndInstrumentId(1, 10)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> settlementService.settle(
                request(1, 10, SettlementRequest.OrderType.SELL, new BigDecimal("4"), new BigDecimal("120.00"))));
    }

    @Test
    void sellRejectedWhenQuantityExceedsHolding() {
        HoldingsEntity existing = new HoldingsEntity(1, 10, new BigDecimal("3"), new BigDecimal("100.00"));
        when(holdingsRepository.findByAccountIdAndInstrumentId(1, 10)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> settlementService.settle(
                request(1, 10, SettlementRequest.OrderType.SELL, new BigDecimal("4"), new BigDecimal("120.00"))));
    }

    private static SettlementRequest request(int accountId, int instrumentId, SettlementRequest.OrderType type,
                                              BigDecimal quantity, BigDecimal pricePerUnit) {
        SettlementRequest request = new SettlementRequest();
        request.setOrderId(1);
        request.setAccountId(accountId);
        request.setInstrumentId(instrumentId);
        request.setOrderType(type);
        request.setQuantity(quantity);
        request.setPricePerUnit(pricePerUnit);
        return request;
    }

    // accountId is JPA-identity-generated (no setter) and unused by HoldingsSettlementService,
    // which only ever reads/writes cashBalance on the AccountEntity it looked up by id.
    private static AccountEntity accountWithBalance(BigDecimal cashBalance) {
        AccountEntity account = new AccountEntity();
        account.setCashBalance(cashBalance);
        account.setCurrency("USD");
        return account;
    }
}
