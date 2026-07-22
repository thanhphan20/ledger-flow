package com.ledgerflow.payment.dtos;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
  private Long userId;
  private BigDecimal amount;
  private String currency;

  /** Caller-supplied key that guarantees exactly-once payment processing. */
  @NotBlank private String idempotencyKey;
}
