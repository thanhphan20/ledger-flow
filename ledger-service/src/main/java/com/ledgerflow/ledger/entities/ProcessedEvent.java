package com.ledgerflow.ledger.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "processed_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent implements Persistable<String> {
  @Id private String eventId;

  private LocalDateTime processedAt;

  @Override
  public String getId() {
    return eventId;
  }

  // eventId is assigned by the caller, not generated, so Spring Data's default null-check
  // "isNew()" heuristic would treat every save as an update (merge) instead of an insert -
  // silently overwriting an existing row rather than throwing the unique-constraint violation
  // the idempotency guard in LedgerEventProcessor relies on. Forcing isNew()=true makes every
  // save() go through persist(), so a duplicate eventId correctly fails loudly.
  @Override
  public boolean isNew() {
    return true;
  }
}
