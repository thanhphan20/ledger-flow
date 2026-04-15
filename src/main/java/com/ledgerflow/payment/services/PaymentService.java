package com.ledgerflow.payment.services;

import com.ledgerflow.payment.entities.Payment;
import com.ledgerflow.payment.enums.PaymentStatus;
import com.ledgerflow.payment.repositories.PaymentRepository;
import com.ledgerflow.ledger.enums.EntryType;
import com.ledgerflow.payment.common.events.PaymentCompletedEvent;
import com.ledgerflow.ledger.services.LedgerService;
import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ApplicationEventPublisher eventPublisher;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;

    @Transactional
    public Payment processPayment(BigDecimal amount, String currency) {
        // 1. Create Payment
        Payment payment = Payment.builder()
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .referenceId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();

        // Save initial pending state
        payment = paymentRepository.save(payment);

        // 2. Simulate processing payment
        payment.setStatus(PaymentStatus.COMPLETED);
        payment = paymentRepository.save(payment);

        // 3. Create Ledger Entry for the processed payment
        ledgerService.createEntry(payment.getId(), amount, currency, EntryType.CREDIT);

        // 4. Publish PaymentCompletedEvent
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                payment.getId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                EntryType.CREDIT
            ));

        return payment;
    }
}
