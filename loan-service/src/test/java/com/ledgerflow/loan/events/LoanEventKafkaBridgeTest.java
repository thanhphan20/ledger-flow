package com.ledgerflow.loan.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import com.ledgerflow.loan.services.LoanService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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
 * Verifies {@link LoanEventKafkaBridge} publishes a loan's approved event to Kafka after the
 * approving transaction commits.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "loan.approved")
class LoanEventKafkaBridgeTest {

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private LoanService loanService;

  @Test
  void publishesToKafkaAfterLoanApprovalCommits() {
    Long loanId = loanService.createLoan(7L, new BigDecimal("500.00"), "USD", 12).getId();

    Loan approved = loanService.approveLoan(loanId);
    assertThat(approved.getStatus()).isEqualTo(LoanStatus.APPROVED);

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
        "spring.json.value.default.type", "com.ledgerflow.contracts.events.LoanApprovedEvent");

    DefaultKafkaConsumerFactory<String, LoanApprovedEvent> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);
    try (Consumer<String, LoanApprovedEvent> consumer = consumerFactory.createConsumer()) {
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "loan.approved");
      final org.apache.kafka.clients.consumer.ConsumerRecord<String, LoanApprovedEvent>[]
          holder = new org.apache.kafka.clients.consumer.ConsumerRecord[1];
      // getSingleRecord aborts if a single poll returns >1 record; the @SpringBootTest context is
      // shared with LoanServiceApprovalTest, whose approves leave stale records on this same
      // EmbeddedKafka topic, so drain and match this loan's event by its unique key instead.
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                ConsumerRecords<String, LoanApprovedEvent> polled =
                    consumer.poll(Duration.ofSeconds(1));
                holder[0] =
                    polled.records("loan.approved").stream()
                        .filter(r -> r.key().equals(loanId.toString()))
                        .findFirst()
                        .orElse(null);
                assertThat(holder[0]).isNotNull();
              });
      assertThat(holder[0].value().getLoanId()).isEqualTo(loanId);
      assertThat(holder[0].value().getAmount()).isEqualByComparingTo("500.00");
    }
  }
}
