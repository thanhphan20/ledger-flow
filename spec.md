# LedgerFlow — System Design

See [AGENTS.md](AGENTS.md) for build commands and repo conventions. This document describes
*what the system is and why it's shaped this way*.

## Context

LedgerFlow started as a single Spring Boot monolith with two logical modules (`payment` and
`ledger`) talking to each other through an in-process Spring `ApplicationEventPublisher`. It
has since been split into two independently-deployable services — `payment-service` and
`ledger-service` — that communicate only through Kafka. The split was driven by a learning
goal (understand real microservices, not a "modular monolith"), which is why the two services
are 100% decoupled: no shared database, no shared JVM, no synchronous calls between them.

## The two services

### payment-service

Owns the `payments` table (its own database: `ledgerflow_payment`). Exposes
`POST /api/v1/payments`, which creates a `Payment` (`PENDING` → `COMPLETED`) and is the only
Kafka **producer** in the system.

Publish path: `PaymentService.processPayment` still fires an in-process
`ApplicationEventPublisher.publishEvent(PaymentCompletedEvent)` inside its `@Transactional`
method, unchanged from the monolith. A separate component, `PaymentEventKafkaBridge`, listens
with `@TransactionalEventListener(phase = AFTER_COMMIT)` and does the actual
`kafkaTemplate.send("payment.completed", paymentId, event)`. This preserves the "only fires
after the DB commit" guarantee without introducing a full transactional outbox table — the
residual risk (process crashes after commit but before the Kafka send completes) is accepted
for now and is why `ReconciliationService` exists (below). A true outbox/CDC pattern is a
candidate future hardening step, not built today.

`ReconciliationService` (`@Scheduled`, every 5 minutes) finds `COMPLETED` payments older than a
5-minute threshold and republishes them to `payment.completed` with a synthesized
`"RECON-{id}-{timestamp}"` event id. This is a **time-threshold heuristic**, not a true
"orphan" check — the original design used a native SQL join against `ledger_entries` to find
payments genuinely missing a ledger entry, but that table now lives in a different database
and the join is no longer possible. The heuristic is safe only because ledger-service's
idempotency guard (below) makes a redundant republish a no-op. A more precise design (an
ack event from ledger-service back to payment-service, e.g. `ledger.entry-posted`, updating a
`ledgerAckAt` column) was deliberately deferred — it's a reasonable next step but adds a third
topic and a second consumer to payment-service before the one-directional flow has proven
itself.

### ledger-service

Owns `ledger_entries`, `processed_events`, and `failed_events` (its own database:
`ledgerflow_ledger`). Is the only Kafka **consumer**. Has no business REST API — Actuator
health only.

Consume path: `LedgerEventHandler.handlePaymentCompleted` is a `@KafkaListener` on
`payment.completed` (replacing the old `@TransactionalEventListener` + spring-retry
`@Retryable`/`@Recover` pair). It delegates to `LedgerEventProcessor.processPaymentCompleted`,
which is where the idempotency guarantee lives:

1. Insert a `ProcessedEvent` row keyed by `eventId`, in its own `REQUIRES_NEW` transaction.
2. If that insert throws a duplicate-key violation (Postgres/H2 SQLState `23505`), log and
   return — this event has already been processed, do nothing further.
3. Otherwise, create and save the `LedgerEntry`.

**Known bug found and fixed during the extraction:** `ProcessedEvent`'s `eventId` is a
manually-assigned `String` (no `@GeneratedValue`). Spring Data JPA's default `save()` decides
`persist()` vs `merge()` based on whether the `@Id` is null — since it's always non-null here,
`save()` was silently calling `merge()` (an upsert) instead of `persist()` (an insert), so a
duplicate `eventId` **updated the existing row instead of throwing**. The idempotency guard
never actually fired. Fixed by implementing `Persistable<String>` with `isNew()` hardcoded to
`true`, forcing `persist()` semantics. This bug would have existed identically on real
Postgres in the original monolith too — it wasn't Kafka-specific, just never caught because
there was no test exercising duplicate delivery until this migration added one
(`LedgerEventHandlerIdempotencyTest`).

Error handling: retries move from spring-retry to Kafka-native container error handling — a
`DefaultErrorHandler` bean (`KafkaErrorHandlingConfig`) with an `ExponentialBackOffWithMaxRetries`
matching the old shape (1s, then 2s, 2 retries). On exhaustion, or on a message that fails to
deserialize at all (`ErrorHandlingDeserializer` wraps the `JsonDeserializer` precisely so a
poison-pill message can't crash the consumer), `FailedEventRecorder` persists a `FailedEvent`
row — this is the dead-letter store, reusing the table that already existed in the monolith.

## ledgerflow-contracts

The shared Kafka message schema: `PaymentCompletedEvent` (`eventId`, `paymentId`, `userId`,
`amount`, `currency`, `type: EntryType`) and `EntryType` (`DEBIT`/`CREDIT`). Extracted here
specifically to fix a smell in the monolith where the event class (in the `payment` package)
imported an enum from the `ledger` package — a cross-service dependency that becomes
impossible once they're actually separate deployables.

## Event flow (happy path)

```
POST /api/v1/payments
        │
        ▼
payment-service: Payment row (PENDING → COMPLETED), committed
        │  (AFTER_COMMIT)
        ▼
PaymentEventKafkaBridge --> Kafka topic "payment.completed" (key = paymentId)
                                        │
                                        ▼
                          ledger-service: @KafkaListener
                                        │
                          ┌─────────────┴─────────────┐
                          ▼                           ▼
                 insert ProcessedEvent        (duplicate eventId?)
                 (REQUIRES_NEW tx)             log + return, no LedgerEntry
                          │
                          ▼
                 insert LedgerEntry
```

On consumer failure: retry with backoff (2 retries) → still failing, or a deserialization
error → `FailedEvent` row (dead-letter), consumer keeps processing subsequent messages.

## Local infrastructure

`docker-compose.yml`: two Postgres containers (one per service database), a single-node Kafka
broker in KRaft mode (no Zookeeper), and a Kafdrop UI for inspecting topics/consumer lag.
`payment-service/Dockerfile` and `ledger-service/Dockerfile` are multi-stage builds whose
context must be the **repo root** (not the module directory), since the build needs to see
the parent `pom.xml` and the `ledgerflow-contracts` module.

## Authentication

`payment-service` requires a JWT on every request except the paths `/api/v1/auth/login`,
`/actuator/health` (the latter must stay open for docker-compose/k8s health probes), and
`/error`. The exemption in `SecurityConfig` is path-based, not method-restricted — it isn't
specifically "only `POST` to `/login`" or "only `GET` to `/health`", even though those are the
only methods actually routed to those paths today. `/error` is exempted for a different reason:
Spring Boot's default error handling forwards any `sendError()` internally to `GET /error`
(e.g. a `@Valid` failure on the login request), and without permitting it the security chain
would intercept that forward and mask the real status code with its own 401 response.
`ledger-service` is unchanged — it has no business REST API (only Actuator health), so there's
nothing there to protect yet; revisit when it gets a real API or an API gateway is introduced.

- **Issuing**: `POST /api/v1/auth/login` (`AuthController`) authenticates against an in-memory
  demo user store (`InMemoryUserDetailsManager`, one user, BCrypt-hashed password — not
  persisted, see "Explicitly out of scope" below), then signs a JWT via `JwtEncoder`
  (`NimbusJwtEncoder`) using a shared HMAC secret. Claims: `sub` (username), `iat`, `exp`
  (~1 hour), a `roles` claim. Wrong credentials → 401.
- **Validating**: `SecurityConfig`'s filter chain is `.oauth2ResourceServer(oauth2 ->
  oauth2.jwt(...))`, backed by a `JwtDecoder` (`NimbusJwtDecoder`) using the *same* shared
  secret — both beans are keyed off one `jwt.secret` property (`JwtConfig`), env-var
  overridable via `JWT_SECRET`, with an obviously-fake ≥32-byte dev default (HS256 requires a
  minimum 256-bit key; a shorter one fails at bean-construction time — the app won't boot, not
  just reject JWT calls).
- **Known limitation, deliberate for this milestone**: this is authentication only, not
  authorization. Any valid JWT from the one demo user can submit a payment for any `userId` in
  the request body — there's no cross-check between the token's `sub` claim and the payment's
  `userId`. That's a natural next step once ownership/authorization matters, not built here.

## Security — current state

`ledger-service`'s `SecurityConfig` is still a wide-open `permitAll()` placeholder (CSRF
disabled, frame options disabled) — deliberate, since it has no protected surface yet (see
above). `payment-service` now requires authentication as described above.

## Explicitly out of scope (deliberate, not forgotten)

These were discussed and intentionally deferred, in roughly this order:

1. **Persisted user store / registration.** The demo login is in-memory, one hardcoded user —
   a persisted `AppUser` table (payment-service already has JPA/Postgres wired up) is the
   natural next step, deliberately not bundled into the JWT-mechanics milestone.
2. **Asymmetric keys / JWKS endpoint.** A shared HMAC secret is the deliberate starting point;
   a real auth-service signing with its own private key and exposing a JWKS endpoint for other
   services to fetch public keys from is more "production-real" but adds infrastructure this
   milestone didn't need yet.
3. **`ledger-service` as a resource server.** No business API to protect yet — see
   "Authentication" above.
4. **API gateway.** Would sit in front of both services once there's a single entry point
   worth centralizing auth at.
5. **Kubernetes deployment.** The eventual target once the services are solid running under
   docker-compose.
6. **Schema registry / Avro.** JSON is deliberately used for `payment.completed` today; this
   is a real future concern once a second event type needs to share a topic or the schema
   needs to evolve safely.
7. **Ack-loop reconciliation.** A `ledgerAckAt` column + a `ledger.entry-posted` ack topic,
   replacing the current time-threshold heuristic — a legitimate next step, but sequenced
   after the one-directional Kafka flow is comfortable, not bundled into it.

Do not "helpfully" start implementing any of the above without it being the explicit ask —
each is a deliberately separate milestone.
