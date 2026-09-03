# AGENTS.md

Guidance for AI coding agents working in this repo. See [spec.md](spec.md) for the system
design (event flow, database ownership, idempotency, what's deliberately out of scope).

## What this project is

A Java learning project (Spring Boot 4, Java 21) that started as a single monolith and has
since been split into three independently-deployable microservices communicating only through
Kafka. The learning goals driving its shape: Java core, Spring Security/JWT, microservices,
and eventually Kubernetes deployment.

## Repo structure

Multi-module Maven build, root `pom.xml` is `packaging: pom` (no source of its own):

```
ledger-flow/
  pom.xml                    parent - dependencyManagement/pluginManagement only
  ledgerflow-contracts/      shared library: Kafka event types, no Spring deps
  payment-service/           owns payments, publishes to Kafka; JWT resource server
  loan-service/              owns loans, publishes to Kafka; JWT resource server
  ledger-service/            consumes from Kafka, posts ledger entries
  docker-compose.yml         local dev: 3 Postgres + Kafka (KRaft) + Kafdrop
  k8s/                       namespace + Postgres manifests (app services not deployed yet)
  .github/workflows/         maven.yml, docker-compose-e2e.yml, k8s-verify.yml
```

Each service module mirrors the same internal layout:
`controllers/ | services/ | repositories/ | entities/ | dtos/ | enums/ | config/ | events/`

## Build & test

```bash
./mvnw clean install              # build + test all 3 modules (reactor build)
./mvnw test                       # tests only
./mvnw -pl payment-service test   # one module only (needs -am if it depends on contracts changes)
./mvnw spotless:apply             # auto-format (Google Java Format) - run before committing
```

Tests need real infra:
- **Smoke tests** (`*ApplicationTests.java`, plain `@SpringBootTest`) connect to a real local
  Postgres at `ledgerflow_payment` / `ledgerflow_ledger` / `ledgerflow_loan` (see each module's
  `src/main/resources/application.yml`). CI provisions all three databases against a single
  `postgres:latest` service container.
- **Integration tests** (`*Test.java` under `events/`) use `@EmbeddedKafka` + an H2 in-memory
  DB, configured via each module's `src/test/resources/application.yml`. **Spring Boot loads
  whichever `application.yml` it finds first on the classpath — it does not merge main and
  test config** — so the test YAML repeats everything the app needs (Kafka producer/consumer
  settings, datasource), not just the overrides. Forgetting this is the most common way a new
  test silently uses the wrong serializer/deserializer and fails confusingly.

`docker compose up` runs the full local stack (all three services + their databases + Kafka +
Kafdrop UI at `localhost:9000`). Docker was not available in the environment these modules
were built in, so the compose file has been reviewed but not run end-to-end — verify it
yourself before trusting it blindly.

## Module boundaries

- **ledgerflow-contracts** — `PaymentCompletedEvent`, `LoanApprovedEvent`, and `EntryType`.
  Only dependency: Lombok (provided). Nothing here should ever depend on Spring, JPA, or
  either service.
- **payment-service** — owns the `payments` table and the `/api/v1/payments` REST API. One of
  two Kafka *producers* in this system.
- **loan-service** — owns the `loans` table and the `/api/v1/loans` REST API (POST create,
  POST `/{id}/approve`, GET `/{id}`). The second Kafka *producer*; publishes `LoanApprovedEvent`
  on approve. JWT-protected like payment-service.
- **ledger-service** — owns `ledger_entries`, `processed_events`, `failed_events`. Is the only
  Kafka *consumer*. Has no public REST API beyond Actuator health.

No service's JPA layer reaches into another's tables anymore (that used to happen via
a native SQL join in the monolith; splitting the databases forced it out — see spec.md).

## Kafka event contract

- **Topic:** `payment.completed`, one partition is enough for a learning setup, keyed by
  `paymentId` (string) for per-payment ordering.
- **Payload:** `com.ledgerflow.contracts.events.PaymentCompletedEvent` — plain Lombok `@Data`
  POJO, JSON-serialized (`spring-kafka`'s `JsonSerializer`/`JsonDeserializer`). No Avro/schema
  registry; the consumer pins `spring.json.value.default.type` explicitly since there's only
  one event type on this topic today.
- If you add a second event type to this topic, you'll need real type-mapping headers instead
  of the pinned default type — don't bolt that on silently, it changes the wire contract.
- **Topic:** `loan.approved`, keyed by `loanId` (string) for per-loan ordering.
- **Payload:** `com.ledgerflow.contracts.events.LoanApprovedEvent` — `eventId`, `loanId`,
  `userId`, `amount`, `currency`; plain Lombok `@Data` POJO, JSON-serialized, no type headers
  (same wire-contract decision as `payment.completed`).
- **Two consumer factories in ledger-service:** the yml default factory pins
  `spring.json.value.default.type` to `PaymentCompletedEvent`, so the `loan.approved` listener
  uses its own consumer factory with `LoanApprovedEvent` pinned instead. If you add a second
  event type to *either* topic, you'll need real type-mapping headers — the pinned default
  type only works while each topic carries exactly one event type.

## Conventions

- **Formatting:** `com.diffplug.spotless` + Google Java Format, enforced in `mvn verify`
  (bound there in `pluginManagement`). Run `mvn spotless:apply` before committing; CI does not
  auto-fix, it just checks.
- **Lombok everywhere:** `@Data`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor` on
  entities/DTOs, `@RequiredArgsConstructor` on Spring components (constructor injection via
  `final` fields), `@Slf4j` for logging.
- **No comments by default.** Only add one for a non-obvious WHY (a hidden constraint, a
  workaround, a subtle invariant) — never to restate what the code already says.
- **No dead code left "just in case."** If you find something unused while touching a file
  (an old entity, an unused method), delete it in the same change rather than leaving it —
  that's how `Account`, `User`, and `LedgerService.createEntry` got dropped during the
  extraction.

## Authentication

`payment-service` and `loan-service` require a JWT (shared-HMAC-secret resource server
pattern) on everything except the paths `/api/v1/auth/login`, `/actuator/health`, and `/error`
— the exemption is path-based in `SecurityConfig`'s matcher, not restricted to a specific HTTP
method (though in practice only `POST`/`GET` are ever routed to those paths respectively).
`/error` has to be exempted too: Spring Boot's default error handling internally forwards any
`sendError()` (e.g. a `@Valid` failure) to `GET /error`, and without the exemption the security
filter chain intercepts that forward and masks the real status with its own 401 — a validation
failure would otherwise come back as "401 Unauthorized" instead of "400 Bad Request".
`ledger-service` is still `permitAll()` — it has no business REST API to protect yet, not an
oversight. Full detail in spec.md's "Authentication" section. This is authentication only, not
authorization: don't assume a JWT's `sub` claim is cross-checked against a request's `userId`
anywhere — it isn't, deliberately, for this milestone.

## What's explicitly not done yet

- **No persisted user store.** Login uses one hardcoded in-memory demo user
  (`InMemoryUserDetailsManager` in `JwtConfig`), not a database-backed one. Don't assume an
  `AppUser`/users table exists anywhere.
- **`k8s/` only deploys the three Postgres databases (plus the namespace), not the app
  services.** No Deployment/Service manifests for `payment-service`/`ledger-service`/
  `loan-service` yet — don't assume they can be `kubectl apply`'d anywhere. Still no API
  gateway, no schema registry, no JWKS/asymmetric keys. See spec.md's "Explicitly out of
  scope" section for the full list.

## Git workflow observed in this repo

Work has been landing as small, single-purpose PRs against `main` (module restructuring, then
infra, then tests, as separate reviewable diffs) rather than one large PR per milestone.
Follow that pattern for new work unless told otherwise: scope a branch to one coherent change,
verify it builds/tests standalone, then open a PR.
