package com.ledgerflow.ledger.services;

import com.ledgerflow.ledger.entities.LedgerEntry;
import com.ledgerflow.ledger.enums.EntryType;
import com.ledgerflow.ledger.repositories.LedgerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerRepository ledgerRepository;

  @Transactional
  public LedgerEntry createEntry(
      Long paymentId, BigDecimal amount, String currency, EntryType type) {
    LedgerEntry entry =
        LedgerEntry.builder()
            .paymentId(paymentId)
            .amount(amount)
            .currency(currency)
            .type(type)
            .createdAt(LocalDateTime.now())
            .build();

    return ledgerRepository.save(entry);
  }
}
