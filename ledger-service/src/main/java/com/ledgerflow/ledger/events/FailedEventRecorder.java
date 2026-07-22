package com.ledgerflow.ledger.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.contracts.events.PaymentCompletedEvent;
import com.ledgerflow.ledger.entities.FailedEvent;
import com.ledgerflow.ledger.repositories.FailedEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Persists events that could not be processed after retries were exhausted (or that failed to
 * deserialize) to the {@code failed_events} dead-letter table, mirroring the old spring-retry
 * {@code @Recover} behavior.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailedEventRecorder {

  private final FailedEventRepository failedEventRepository;
  private final ObjectMapper objectMapper;

  public void recordProcessingFailure(PaymentCompletedEvent event, Exception error) {
    log.error(
        "Exhausted retries for event {}. Saving to failed_events.", event.getEventId(), error);
    save(
        event.getEventId(), event.getClass().getSimpleName(), serialize(event), error.getMessage());
  }

  public void recordDeserializationFailure(String rawRecordDescription, Exception error) {
    log.error(
        "Failed to deserialize Kafka record {}. Saving to failed_events.",
        rawRecordDescription,
        error);
    save(
        "unknown-" + rawRecordDescription,
        "DeserializationError",
        rawRecordDescription,
        error.getMessage());
  }

  private void save(String eventId, String eventType, String payload, String errorMessage) {
    failedEventRepository.save(
        FailedEvent.builder()
            .eventId(eventId)
            .eventType(eventType)
            .payload(payload)
            .errorMessage(errorMessage)
            .createdAt(LocalDateTime.now())
            .build());
  }

  private String serialize(PaymentCompletedEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize event payload", ex);
      return "unknown";
    }
  }
}
