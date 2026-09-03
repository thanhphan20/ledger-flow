package com.ledgerflow.ledger.events;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import com.ledgerflow.ledger.services.LedgerEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventHandler {

  private final LedgerEventProcessor ledgerEventProcessor;

  @KafkaListener(topics = "payment.completed", groupId = "ledger-service")
  public void handlePaymentCompleted(PaymentCompletedEvent event) {
    log.info("Handling payment completed event: {}", event.getEventId());
    ledgerEventProcessor.processPaymentCompleted(event);
  }

  @KafkaListener(
      topics = "loan.approved",
      groupId = "ledger-service",
      containerFactory = "loanListenerContainerFactory")
  public void handleLoanApproved(LoanApprovedEvent event) {
    log.info("Handling loan approved event: {}", event.getEventId());
    ledgerEventProcessor.processLoanApproved(event);
  }
}
