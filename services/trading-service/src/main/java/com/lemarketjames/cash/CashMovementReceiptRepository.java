package com.lemarketjames.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CashMovementReceiptRepository extends JpaRepository<CashMovementReceipt, UUID> {
}
