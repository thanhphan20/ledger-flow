package com.ledgerflow.payment.events;

import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link PaymentCompletedEvent} to Kafka. Listening AFTER_COMMIT preserves
 * the guarantee that ledger-service never sees a payment that hasn't actually been committed to the
 * payment-service database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventKafkaBridge {

  private static final String TOPIC = "payment.completed";

  // Injected as <Object, Object> to match Spring Boot's auto-configured KafkaTemplate bean
  // exactly (generics are invariant, so a more specific requested type wouldn't be satisfied by
  // it); the JSON serializer configured in application.yml still serializes the actual event
  // correctly at runtime regardless of this compile-time type.
  private final KafkaTemplate<Object, Object> kafkaTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPaymentCompleted(PaymentCompletedEvent event) {
    log.info("Publishing payment completed event {} to {}", event.getEventId(), TOPIC);
    kafkaTemplate.send(TOPIC, event.getPaymentId().toString(), event);
  }
}
