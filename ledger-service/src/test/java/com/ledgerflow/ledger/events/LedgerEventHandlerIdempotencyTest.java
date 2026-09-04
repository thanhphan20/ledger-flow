package com.ledgerflow.ledger.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ledgerflow.contracts.events.EntryType;
import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import com.ledgerflow.ledger.repositories.LedgerRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
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
 * Verifies the idempotency guarantee that used to live in {@code LedgerEventHandler}'s spring-retry
 * path is preserved now that events arrive over Kafka: publishing the same event twice must only
 * ever result in one {@code LedgerEntry} row.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "payment.completed")
class LedgerEventHandlerIdempotencyTest {

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private LedgerRepository ledgerRepository;

  @Test
  void duplicateEventOnlyProducesOneLedgerEntry() {
    PaymentCompletedEvent event =
        new PaymentCompletedEvent(
            "test-event-duplicate", 42L, 7L, new BigDecimal("10.00"), "USD", EntryType.CREDIT);

    KafkaTemplate<String, PaymentCompletedEvent> producer = testProducer();
    producer.send("payment.completed", event.getPaymentId().toString(), event);
    producer.send("payment.completed", event.getPaymentId().toString(), event);
    producer.flush();
    producer.destroy();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        ledgerRepository.findAll().stream()
                            .filter(entry -> Objects.equals(entry.getPaymentId(), 42L))
                            .count())
                    .isEqualTo(1));
  }

  private KafkaTemplate<String, PaymentCompletedEvent> testProducer() {
    Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
    producerProps.put(
        "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
    producerProps.put("value.serializer", JsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
  }
}
