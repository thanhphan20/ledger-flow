package com.ledgerflow.ledger.services;

import com.ledgerflow.ledger.entities.LedgerEntry;
import com.ledgerflow.ledger.entities.ProcessedEvent;
import com.ledgerflow.ledger.repositories.LedgerRepository;
import com.ledgerflow.ledger.repositories.ProcessedEventRepository;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import java.sql.SQLException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerEventProcessor {

  private final LedgerRepository ledgerRepository;
  private final ProcessedEventRepository processedEventRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processPaymentCompleted(PaymentCompletedEvent event) {
    try {
      processedEventRepository.saveAndFlush(
          ProcessedEvent.builder()
              .eventId(event.getEventId())
              .processedAt(LocalDateTime.now())
              .build());
    } catch (DataIntegrityViolationException ex) {
      if (!isDuplicateKeyViolation(ex)) {
        throw ex;
      }
      log.info("Ledger: skipping already processed event {}", event.getEventId());
      return;
    }

    LedgerEntry entry =
        LedgerEntry.builder()
            .accountId(event.getUserId())
            .paymentId(event.getPaymentId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .type(event.getType())
            .createdAt(LocalDateTime.now())
            .build();

    ledgerRepository.save(entry);
  }

  private boolean isDuplicateKeyViolation(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return "23505".equals(sqlException.getSQLState());
      }
      current = current.getCause();
    }
    return false;
  }
}
