package com.ledgerflow.ledger.repositories;

import com.ledgerflow.ledger.entities.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {}
