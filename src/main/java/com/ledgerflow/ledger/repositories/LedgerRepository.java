package com.ledgerflow.ledger.repositories;

import com.ledgerflow.ledger.entities.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {}
