package com.ledgerflow.ledger.entities;

import com.ledgerflow.contracts.events.EntryType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ledger_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long accountId;

  private Long paymentId;

  private BigDecimal amount;

  private String currency;

  @Enumerated(EnumType.STRING)
  private EntryType type; // e.g., DEBIT or CREDIT

  private LocalDateTime createdAt;
}
