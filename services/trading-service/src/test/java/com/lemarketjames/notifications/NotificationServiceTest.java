package com.lemarketjames.notifications;

import com.lemarketjames.common.domain.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What becomes a notification, and whose notifications a client reads. */
class NotificationServiceTest {

    static final Instant WHEN = Instant.parse("2026-10-08T14:30:00Z");

    final NotificationRepository stored = mock(NotificationRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final NotificationService service = new NotificationService(stored, accounts);

    @Test
    void aStatusChangeIsStoredForTheOrdersAccount() {
        assertTrue(service.record(new OrderStatusChangedMessage(42, 7, "PENDING", "FILLED", WHEN)));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(stored).save(saved.capture());
        assertEquals(7, saved.getValue().getAccountId());
        assertEquals(42, saved.getValue().getOrderId());
        assertEquals("PENDING", saved.getValue().getPreviousStatus());
        assertEquals("FILLED", saved.getValue().getStatus());
        assertEquals("Order #42 is now FILLED (was PENDING)", saved.getValue().getMessage());
        assertEquals(WHEN, saved.getValue().getOccurredAt());
    }

    /** Kafka redelivers after a restart or a failed commit; the client must not be told twice. */
    @Test
    void aStatusChangeThatIsAlreadyStoredIsNotStoredAgain() {
        when(stored.existsByOrderIdAndStatus(42, "FILLED")).thenReturn(true);

        assertFalse(service.record(new OrderStatusChangedMessage(42, 7, "PENDING", "FILLED", WHEN)));

        verify(stored, never()).save(any());
    }

    @Test
    void anEventWithoutAFieldANotificationNeedsIsSkipped() {
        for (OrderStatusChangedMessage incomplete : List.of(
                new OrderStatusChangedMessage(null, 7, "PENDING", "FILLED", WHEN),
                new OrderStatusChangedMessage(42, null, "PENDING", "FILLED", WHEN),
                new OrderStatusChangedMessage(42, 7, null, "FILLED", WHEN),
                new OrderStatusChangedMessage(42, 7, "PENDING", null, WHEN),
                new OrderStatusChangedMessage(42, 7, "PENDING", "FILLED", null))) {
            assertFalse(service.record(incomplete), incomplete.toString());
        }

        verify(stored, never()).save(any());
    }

    @Test
    void aClientReadsTheNewestNotificationsOfTheirOwnAccount() {
        List<Notification> newest = List.of(
            new Notification(new OrderStatusChangedMessage(42, 7, "PENDING", "FILLED", WHEN), "told"));
        when(accounts.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
        when(stored.findByAccountIdOrderByOccurredAtDescNotificationIdDesc(7, PageRequest.of(0, NotificationService.LATEST)))
            .thenReturn(newest);

        assertSame(newest, service.latestFor("alice"));
    }

    @Test
    void aLoginWithoutAnAccountHasNoNotificationsToRead() {
        when(accounts.findAccountIdByUsername("ops")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.latestFor("ops"));
    }
}
