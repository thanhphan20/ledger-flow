package com.ledgerflow.loan.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import com.ledgerflow.loan.services.LoanService;
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
      ConsumerRecord<String, LoanApprovedEvent> record =
          KafkaTestUtils.getSingleRecord(consumer, "loan.approved", Duration.ofSeconds(10));
      assertThat(record.key()).isEqualTo(loanId.toString());
      assertThat(record.value().getLoanId()).isEqualTo(loanId);
      assertThat(record.value().getAmount()).isEqualByComparingTo("500.00");
    }
  }
}
