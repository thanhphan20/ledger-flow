package com.ledgerflow.payment.repositories;

import com.ledgerflow.payment.entities.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  @Query(
      value =
          "SELECT p.* FROM payments p "
              + "LEFT JOIN ledger_entries l ON p.id = l.payment_id "
              + "WHERE p.status = 'COMPLETED' AND l.id IS NULL",
      nativeQuery = true)
  List<Payment> findCompletedPaymentsWithoutLedgerEntries();
}
