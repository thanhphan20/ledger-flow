package com.ledgerflow.ledger.services;

import com.ledgerflow.ledger.enums.EntryType;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import com.ledgerflow.payment.entities.Payment;
import com.ledgerflow.payment.repositories.PaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

  private final PaymentRepository paymentRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Periodically check for COMPLETED payments that might have missed their ledger entry creation.
   * Runs every 5 minutes in this example.
   */
  @Scheduled(fixedRate = 300000)
  @Transactional(readOnly = true)
  public void reconcilePayments() {
    log.info("Starting payment-ledger reconciliation job...");

    // Find payments that are COMPLETED but don't have a matching LedgerEntry
    // Note: In a real production system, you'd use a more efficient native query or specialized
    // reporting DB.
    // For this demonstration, we'll implement a simple check.

    // We'll add a method to PaymentRepository for this.
    List<Payment> orphanedPayments = findOrphanedPayments();

    if (orphanedPayments.isEmpty()) {
      log.info("Reconciliation complete. No orphaned payments found.");
      return;
    }

    log.warn("Found {} orphaned payments. Re-triggering events...", orphanedPayments.size());

    for (Payment payment : orphanedPayments) {
      log.info("Re-publishing event for payment ID: {}", payment.getId());
      eventPublisher.publishEvent(
          new PaymentCompletedEvent(
              "RECON-" + payment.getId() + "-" + System.currentTimeMillis(),
              payment.getId(),
              payment.getUserId(),
              payment.getAmount(),
              payment.getCurrency(),
              EntryType.CREDIT));
    }
  }

  private List<Payment> findOrphanedPayments() {
    // This query finds COMPLETED payments where no record exists in ledger_entries with that
    // payment_id
    // We'll need to define this in PaymentRepository using a JOIN or NOT EXISTS.
    return paymentRepository.findCompletedPaymentsWithoutLedgerEntries();
  }
}
