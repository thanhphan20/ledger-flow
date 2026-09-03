package com.ledgerflow.loan.dtos;

import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {
  private Long id;
  private Long userId;
  private BigDecimal amount;
  private String currency;
  private Integer termMonths;
  private LoanStatus status;
  private LocalDateTime createdAt;

  public static LoanResponse from(Loan loan) {
    return LoanResponse.builder()
        .id(loan.getId())
        .userId(loan.getUserId())
        .amount(loan.getAmount())
        .currency(loan.getCurrency())
        .termMonths(loan.getTermMonths())
        .status(loan.getStatus())
        .createdAt(loan.getCreatedAt())
        .build();
  }
}
