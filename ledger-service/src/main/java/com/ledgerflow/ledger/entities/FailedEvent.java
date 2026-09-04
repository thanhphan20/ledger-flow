package com.ledgerflow.ledger.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "failed_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String eventId;

  private String eventType;

  @Column(columnDefinition = "TEXT")
  private String payload;

  private String errorMessage;

  private LocalDateTime createdAt;
}
