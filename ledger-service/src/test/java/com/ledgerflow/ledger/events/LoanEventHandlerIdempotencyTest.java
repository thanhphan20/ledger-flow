package com.ledgerflow.ledger.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.ledger.repositories.LedgerRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Verifies the idempotency guarantee holds for loan events: publishing the same LoanApprovedEvent
 * twice must only ever result in one CREDIT LedgerEntry row with loanId set.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"payment.completed", "loan.approved"})
class LoanEventHandlerIdempotencyTest {

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private LedgerRepository ledgerRepository;

  @Test
  void duplicateLoanEventOnlyProducesOneLedgerEntry() {
    LoanApprovedEvent event =
        new LoanApprovedEvent(
            "test-loan-event-duplicate", 42L, 7L, new BigDecimal("500.00"), "USD");

    KafkaTemplate<String, LoanApprovedEvent> producer = testProducer();
    producer.send("loan.approved", event.getLoanId().toString(), event);
    producer.send("loan.approved", event.getLoanId().toString(), event);
    producer.flush();
    producer.destroy();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        ledgerRepository.findAll().stream()
                            .filter(entry -> entry.getLoanId() != null)
                            .filter(entry -> entry.getLoanId().equals(42L))
                            .count())
                    .isEqualTo(1));
  }

  private KafkaTemplate<String, LoanApprovedEvent> testProducer() {
    Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
    producerProps.put(
        "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
    producerProps.put("value.serializer", JsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
  }
}
