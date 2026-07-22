package com.ledgerflow.payment.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import com.ledgerflow.payment.services.PaymentService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Verifies {@link PaymentEventKafkaBridge} actually publishes a payment's completed event to Kafka
 * once {@link PaymentService#processPayment} has committed - the property that used to be
 * guaranteed by {@code @TransactionalEventListener(AFTER_COMMIT)} alone, now spanning an in-process
 * event plus a Kafka publish.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "payment.completed")
class PaymentEventKafkaBridgeTest {

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private PaymentService paymentService;

  @Test
  void publishesToKafkaAfterPaymentCommits() {
    paymentService.processPayment(1L, new BigDecimal("25.00"), "USD", UUID.randomUUID().toString());

    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(
            "test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
    consumerProps.put(
        "key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class);
    consumerProps.put("value.deserializer", ErrorHandlingDeserializer.class);
    consumerProps.put("spring.deserializer.value.delegate.class", JsonDeserializer.class);
    consumerProps.put("spring.json.trusted.packages", "com.ledgerflow.contracts.events");
    consumerProps.put("spring.json.use.type.headers", false);
    consumerProps.put(
        "spring.json.value.default.type", "com.ledgerflow.contracts.events.PaymentCompletedEvent");

    DefaultKafkaConsumerFactory<String, PaymentCompletedEvent> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);
    try (Consumer<String, PaymentCompletedEvent> consumer = consumerFactory.createConsumer()) {
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "payment.completed");
      ConsumerRecord<String, PaymentCompletedEvent> record =
          KafkaTestUtils.getSingleRecord(consumer, "payment.completed", Duration.ofSeconds(10));
      assertThat(record.value().getAmount()).isEqualByComparingTo("25.00");
    }
  }
}
