package com.ledgerflow.ledger.common.events;

import com.ledgerflow.ledger.enums.EntryType;
import com.ledgerflow.ledger.services.LedgerService;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerEventHandler {

  private final LedgerService ledgerService;

  @EventListener
  public void handlePaymentCompleted(PaymentCompletedEvent event) {
    System.out.println(
        "Ledger: Payment completed, creating user account if not exists: " + event.getPaymentId());
    ledgerService.createEntry(
        event.getPaymentId(), event.getAmount(), event.getCurrency(), EntryType.CREDIT);
  }
}
