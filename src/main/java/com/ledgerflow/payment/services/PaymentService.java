package com.ledgerflow.payment.services;

import com.ledgerflow.ledger.enums.EntryType;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import com.ledgerflow.payment.entities.Payment;
import com.ledgerflow.payment.enums.PaymentStatus;
import com.ledgerflow.payment.repositories.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

  private final ApplicationEventPublisher eventPublisher;
  private final PaymentRepository paymentRepository;

  @Transactional
  public Payment processPayment(
      Long userId, BigDecimal amount, String currency, String idempotencyKey) {
    // 1. Check for existing payment (Idempotency)
    return paymentRepository
        .findByIdempotencyKey(idempotencyKey)
        .orElseGet(
            () -> {
              // 2. Create Payment
              Payment payment =
                  Payment.builder()
                      .userId(userId)
                      .amount(amount)
                      .currency(currency)
                      .status(PaymentStatus.PENDING)
                      .idempotencyKey(idempotencyKey)
                      .createdAt(LocalDateTime.now())
                      .build();

              // Save initial pending state
              payment = paymentRepository.save(payment);
              payment.setReferenceId(payment.getId().toString());
              payment = paymentRepository.save(payment);

              // 3. Simulate processing payment
              payment.setStatus(PaymentStatus.COMPLETED);
              payment = paymentRepository.save(payment);

              // 4. Publish PaymentCompletedEvent
              eventPublisher.publishEvent(
                  new PaymentCompletedEvent(
                      payment.getId().toString(),
                      payment.getId(),
                      payment.getUserId(),
                      payment.getAmount(),
                      payment.getCurrency(),
                      EntryType.CREDIT));

              return payment;
            });
  }
}
