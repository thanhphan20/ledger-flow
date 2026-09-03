package com.ledgerflow.loan.events;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link LoanApprovedEvent} to Kafka AFTER_COMMIT, so ledger-service never
 * sees a loan approval that hasn't committed to the loan-service database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventKafkaBridge {

  private static final String TOPIC = "loan.approved";

  // Injected as <Object, Object> to match Spring Boot's auto-configured KafkaTemplate bean
  // exactly (generics are invariant); the JSON serializer configured in application.yml still
  // serializes the actual event correctly at runtime regardless of this compile-time type.
  private final KafkaTemplate<Object, Object> kafkaTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLoanApproved(LoanApprovedEvent event) {
    log.info("Publishing loan approved event {} to {}", event.getEventId(), TOPIC);
    kafkaTemplate.send(TOPIC, event.getLoanId().toString(), event);
  }
}
