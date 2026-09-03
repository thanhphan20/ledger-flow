# Loan Service Design

Status: approved
Date: 2026-09-03

## Summary

Add a third microservice, `loan-service`, as a standalone loan domain. It mirrors
payment-service's structure, publishes a `LoanApprovedEvent` to a new `loan.approved`
Kafka topic on approval, and ledger-service consumes that topic and posts CREDIT
entries into the existing `ledger_entries` table.

## Goals

- Repeat the payment-service pattern (REST + JWT + own DB + Kafka produce) in a new
  service, giving the system a second producer and a second topic.
- Exercise the first cross-service extension of the event contract: a new event type
  in `ledgerflow-contracts` consumed by ledger-service.
- Follow repo conventions: multi-module Maven, Lombok, Spotless, small PRs.

## Non-goals

- No interaction between loan-service and payment-service (standalone domain).
- No loan repayment schedules, interest calculation, or rejection workflow.
- No changes to the `payment.completed` topic or its wire contract.
- No new schema registry or type-mapping headers; `loan.approved` pins its default
  type exactly like `payment.completed` does today.
- No new auth mechanism: same shared-HMAC JWT resource-server pattern.

## Architecture

```
loan-service (new)                    ledger-service (extended)
  REST /api/v1/loans                    @KafkaListener "loan.approved"
  JWT resource server                   -> LedgerEventHandler.handleLoanApproved
  own DB: ledgerflow_loan               -> LedgerEventProcessor.processLoanApproved
  on approve:                           -> LedgerEntry (CREDIT, loanId set)
    publish LoanApprovedEvent             dedup via processed_events (eventId)
    to topic "loan.approved"              failures -> failed_events
    keyed by loanId
```

## Module plan

### ledgerflow-contracts

- Add `com.ledgerflow.contracts.events.LoanApprovedEvent`:
  `eventId (String UUID), loanId (Long), userId (Long), amount (BigDecimal),
  currency (String)` — plain Lombok `@Data` POJO, same style as
  `PaymentCompletedEvent`. No `EntryType` field: entries are always CREDIT.
- `EntryType` unchanged (`CREDIT` already exists).

### loan-service (new Maven module)

Mirrors payment-service layout: `controllers | services | repositories | entities |
dtos | enums | config | events`.

- Entity `Loan` (`loans` table): `id, userId, amount, currency, termMonths,
  status, createdAt`. `LoanStatus` enum: `PENDING`, `APPROVED`.
- DTOs: `CreateLoanRequest` (userId, amount, currency, termMonths, with
  `@Valid` constraints), `LoanResponse`.
- API under `/api/v1/loans`:
  - `POST /api/v1/loans` — create a loan in `PENDING` state.
  - `POST /api/v1/loans/{id}/approve` — approve a PENDING loan, publish
    `LoanApprovedEvent`. Approving an already-approved loan is a 409; unknown id
    is a 404. Approval is not idempotent-keyed (YAGNI; payments already
    demonstrate the idempotency-key pattern).
  - `GET /api/v1/loans/{id}` — fetch one.
- Publish path: same in-process event + Kafka bridge pattern as payment-service
  (`ApplicationEventPublisher` -> `LoanEventKafkaBridge` -> `KafkaTemplate`).
- Security: copy of payment-service's `SecurityConfig`/`JwtConfig` — shared HMAC
  secret, exemptions for `/api/v1/auth/login`, `/actuator/health`, `/error`.
- Config: `ledgerflow_loan` datasource, `spring.json.value.default.type` pinned to
  `LoanApprovedEvent` on topic `loan.approved`, key by `loanId` string.

### ledger-service (extended)

- Second `@KafkaListener` on `loan.approved` with its own consumer factory (same
  JSON deserializer setup, pinned default type `LoanApprovedEvent`).
- `LedgerEventHandler.handleLoanApproved` -> `LedgerEventProcessor.processLoanApproved`:
  insert `processed_events` row keyed by `eventId` (existing dedup contract),
  insert `LedgerEntry` with `type=CREDIT`, `accountId=userId`, `loanId` set,
  `paymentId` null (mirrors how `processPaymentCompleted` maps
  `accountId=userId`).
  Malformed/duplicate handling and `FailedEventRecorder` path unchanged.
- `LedgerEntry` gains one nullable `loanId` column. `paymentId` stays as-is; no
  rename to a generic source column.

## Infrastructure

- docker-compose: add `loan-db` (third Postgres) and `loan-service` container,
  wired like the existing pair.
- k8s: add a `loan-db` StatefulSet/Service manifest next to the existing Postgres
  ones. App services remain out of scope for k8s.

## Testing

- **Smoke** (`LoanServiceApplicationTests`): `@SpringBootTest` against real local
  Postgres `ledgerflow_loan`, mirroring the other smoke tests.
- **Integration** (`events/`, `@EmbeddedKafka` + H2): end-to-end
  apply->approve->event->ledger entry, plus a duplicate-event idempotency test
  mirroring `LedgerEventHandlerIdempotencyTest`. Test `application.yml` repeats all
  required config (the main/test YAML merge gotcha from AGENTS.md).
- Payment/ledger existing suites must stay green.

## Landing plan

Three small PRs against `main`, per repo convention:

1. `ledgerflow-contracts` + new `loan-service` module (CRUD, JWT, producer) + its
   tests.
2. ledger-service: `loanId` column, second listener, handler/processor + tests.
3. docker-compose + k8s manifests.
