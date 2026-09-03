package com.ledgerflow.loan.services;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import com.ledgerflow.loan.repositories.LoanRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LoanService {

  private final ApplicationEventPublisher eventPublisher;
  private final LoanRepository loanRepository;

  @Transactional
  public Loan createLoan(Long userId, BigDecimal amount, String currency, Integer termMonths) {
    Loan loan =
        Loan.builder()
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .termMonths(termMonths)
            .status(LoanStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
    return loanRepository.save(loan);
  }

  @Transactional
  public Loan approveLoan(Long loanId) {
    Loan loan =
        loanRepository
            .findById(loanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
    if (loan.getStatus() == LoanStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Loan already approved");
    }
    loan.setStatus(LoanStatus.APPROVED);
    loan = loanRepository.save(loan);

    eventPublisher.publishEvent(
        new LoanApprovedEvent(
            UUID.randomUUID().toString(),
            loan.getId(),
            loan.getUserId(),
            loan.getAmount(),
            loan.getCurrency()));
    return loan;
  }

  public Loan getLoan(Long loanId) {
    return loanRepository
        .findById(loanId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
  }
}
