package com.ledgerflow.payment.services;

import com.ledgerflow.contracts.events.EntryType;
import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import com.ledgerflow.payment.entities.Payment;
import com.ledgerflow.payment.enums.PaymentStatus;
import com.ledgerflow.payment.repositories.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

  private static final String TOPIC = "payment.completed";
  private static final int STALE_THRESHOLD_MINUTES = 5;

  private final PaymentRepository paymentRepository;

  // See PaymentEventKafkaBridge for why this is <Object, Object> rather than a more specific type.
  private final KafkaTemplate<Object, Object> kafkaTemplate;

  /**
   * Periodically re-publish COMPLETED payments that have been sitting for longer than the stale
   * threshold, in case the original Kafka publish was lost. Runs every 5 minutes.
   */
  @Scheduled(fixedRate = 300000)
  @Transactional(readOnly = true)
  public void reconcilePayments() {
    log.info("Starting payment reconciliation job...");

    LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
    List<Payment> stalePayments =
        paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.COMPLETED, threshold);

    if (stalePayments.isEmpty()) {
      log.info("Reconciliation complete. No stale payments found.");
      return;
    }

    log.warn("Found {} stale payments. Re-publishing events...", stalePayments.size());

    for (Payment payment : stalePayments) {
      log.info("Re-publishing event for payment ID: {}", payment.getId());
      PaymentCompletedEvent event =
          new PaymentCompletedEvent(
              "RECON-" + payment.getId() + "-" + System.currentTimeMillis(),
              payment.getId(),
              payment.getUserId(),
              payment.getAmount(),
              payment.getCurrency(),
              EntryType.CREDIT);
      kafkaTemplate.send(TOPIC, payment.getId().toString(), event);
    }
  }
}
