package com.ledgerflow.payment.controllers;

import com.ledgerflow.payment.dtos.PaymentRequest;
import com.ledgerflow.payment.entities.Payment;
import com.ledgerflow.payment.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Endpoints for managing payments")
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping
  @Operation(
      summary = "Process a new payment",
      description = "Creates a new payment and publishes a completion event.")
  @ApiResponse(responseCode = "200", description = "Payment processed successfully")
  public ResponseEntity<Payment> processPayment(@RequestBody PaymentRequest request) {
    Payment payment =
        paymentService.processPayment(
            request.getUserId(),
            request.getAmount(),
            request.getCurrency(),
            request.getIdempotencyKey());
    return ResponseEntity.ok(payment);
  }
}
