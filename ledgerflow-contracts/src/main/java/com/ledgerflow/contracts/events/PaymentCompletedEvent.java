package com.ledgerflow.contracts.events;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
  private String eventId;
  private Long paymentId;
  private Long userId;
  private BigDecimal amount;
  private String currency;
  private EntryType type;
}
