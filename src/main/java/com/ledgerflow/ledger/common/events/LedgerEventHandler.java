package com.ledgerflow.ledger.common.events;

import com.ledgerflow.ledger.services.LedgerEventProcessor;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LedgerEventHandler {
  private final LedgerEventProcessor ledgerEventProcessor;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePaymentCompleted(PaymentCompletedEvent event) {
    ledgerEventProcessor.processPaymentCompleted(event);
  }
}
