package com.ledgerflow.loan.controllers;

import com.ledgerflow.loan.dtos.CreateLoanRequest;
import com.ledgerflow.loan.dtos.LoanResponse;
import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.services.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loan", description = "Endpoints for managing loans")
public class LoanController {

  private final LoanService loanService;

  @PostMapping
  @Operation(summary = "Create a new loan application")
  @ApiResponse(responseCode = "200", description = "Loan created in PENDING state")
  public ResponseEntity<LoanResponse> createLoan(@Valid @RequestBody CreateLoanRequest request) {
    Loan loan =
        loanService.createLoan(
            request.getUserId(),
            request.getAmount(),
            request.getCurrency(),
            request.getTermMonths());
    return ResponseEntity.ok(LoanResponse.from(loan));
  }

  @PostMapping("/{id}/approve")
  @Operation(summary = "Approve a pending loan and publish LoanApprovedEvent")
  @ApiResponse(responseCode = "200", description = "Loan approved")
  @ApiResponse(responseCode = "409", description = "Loan already approved")
  public ResponseEntity<LoanResponse> approveLoan(@PathVariable Long id) {
    return ResponseEntity.ok(LoanResponse.from(loanService.approveLoan(id)));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch a loan by id")
  @ApiResponse(responseCode = "200", description = "Loan found")
  public ResponseEntity<LoanResponse> getLoan(@PathVariable Long id) {
    return ResponseEntity.ok(LoanResponse.from(loanService.getLoan(id)));
  }
}
