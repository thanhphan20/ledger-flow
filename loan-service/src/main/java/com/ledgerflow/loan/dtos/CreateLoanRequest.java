package com.ledgerflow.loan.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoanRequest {
  @NotNull private Long userId;

  @NotNull
  @DecimalMin(value = "0.01")
  private BigDecimal amount;

  @NotBlank private String currency;

  @NotNull @Positive private Integer termMonths;
}
