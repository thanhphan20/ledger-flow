package com.ledgerflow.ledger.config;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import com.ledgerflow.ledger.events.FailedEventRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Container-level Kafka error handling, replacing the old spring-retry
 * {@code @Retryable}/{@code @Recover} pair. Retries 2 more times (matching the old maxAttempts=3)
 * with the same 1s-then-2s backoff, then hands the record to {@link FailedEventRecorder} instead of
 * crashing the consumer.
 */
@Configuration
@RequiredArgsConstructor
public class KafkaErrorHandlingConfig {

  private final FailedEventRecorder failedEventRecorder;

  @Bean
  public DefaultErrorHandler kafkaErrorHandler() {
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
    backOff.setInitialInterval(1000L);
    backOff.setMultiplier(2.0);

    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            (record, exception) -> {
              if (record.value() instanceof PaymentCompletedEvent event) {
                failedEventRecorder.recordProcessingFailure(event, exception);
              } else if (record.value() instanceof LoanApprovedEvent loanEvent) {
                failedEventRecorder.recordProcessingFailure(loanEvent, exception);
              } else {
                String description =
                    "%s-partition%d-offset%d"
                        .formatted(record.topic(), record.partition(), record.offset());
                failedEventRecorder.recordDeserializationFailure(description, exception);
              }
            },
            backOff);
    return errorHandler;
  }
}
