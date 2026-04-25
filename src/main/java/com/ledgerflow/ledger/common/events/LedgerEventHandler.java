package com.ledgerflow.ledger.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.ledger.entities.FailedEvent;
import com.ledgerflow.ledger.repositories.FailedEventRepository;
import com.ledgerflow.ledger.services.LedgerEventProcessor;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventHandler {
  private final LedgerEventProcessor ledgerEventProcessor;
  private final FailedEventRepository failedEventRepository;
  private final ObjectMapper objectMapper;

  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2))
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePaymentCompleted(PaymentCompletedEvent event) {
    log.info("Handling payment completed event: {}", event.getEventId());
    ledgerEventProcessor.processPaymentCompleted(event);
  }

  @Recover
  public void recover(Exception e, PaymentCompletedEvent event) {
    log.error("Exhausted retries for event {}. Saving to failed_events.", event.getEventId(), e);
    String payload = "unknown";
    try {
      payload = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize event payload", ex);
    }

    failedEventRepository.save(
        FailedEvent.builder()
            .eventId(event.getEventId())
            .eventType(event.getClass().getSimpleName())
            .payload(payload)
            .errorMessage(e.getMessage())
            .createdAt(LocalDateTime.now())
            .build());
  }
}
