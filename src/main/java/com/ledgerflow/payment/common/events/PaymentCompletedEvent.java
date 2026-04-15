package com.ledgerflow.payment.common.events;

import com.ledgerflow.ledger.enums.EntryType;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long paymentId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private EntryType type;
}
