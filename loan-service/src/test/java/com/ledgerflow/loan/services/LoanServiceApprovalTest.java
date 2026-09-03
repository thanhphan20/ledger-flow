package com.ledgerflow.loan.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "loan.approved")
class LoanServiceApprovalTest {

  @Autowired private LoanService loanService;

  private Long createPendingLoan() {
    return loanService.createLoan(7L, new BigDecimal("1000.00"), "USD", 12).getId();
  }

  @Test
  void approveUnknownLoanReturns404() {
    assertThatThrownBy(() -> loanService.approveLoan(999_999L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void doubleApproveReturns409() {
    Long id = createPendingLoan();
    loanService.approveLoan(id);

    assertThatThrownBy(() -> loanService.approveLoan(id))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void approveSetsApprovedStatus() {
    Long id = createPendingLoan();

    Loan approved = loanService.approveLoan(id);

    assertThat(approved.getStatus()).isEqualTo(LoanStatus.APPROVED);
    assertThat(approved.getId()).isEqualTo(id);
  }
}
