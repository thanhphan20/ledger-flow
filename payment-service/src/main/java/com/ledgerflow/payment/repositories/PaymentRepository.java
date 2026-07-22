package com.ledgerflow.payment.repositories;

import com.ledgerflow.payment.entities.Payment;
import com.ledgerflow.payment.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  /**
   * ledger_entries now lives in a separate database, so we can no longer join against it to find
   * orphaned payments. Instead, reconciliation resends any payment that's been COMPLETED for longer
   * than the given threshold; ledger-service's idempotency guard makes a resend to an
   * already-processed payment a safe no-op.
   */
  List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime threshold);
}
