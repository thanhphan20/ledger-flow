# AGENTS.md

Guidance for AI coding agents working in this repo. See [spec.md](spec.md) for the system
design (event flow, database ownership, idempotency, what's deliberately out of scope).

## What this project is

A Java learning project (Spring Boot 4, Java 21) that started as a single monolith and has
since been split into two independently-deployable microservices communicating only through
Kafka. The learning goals driving its shape: Java core, Spring Security/JWT, microservices,
and eventually Kubernetes deployment.

## Repo structure

Multi-module Maven build, root `pom.xml` is `packaging: pom` (no source of its own):

```
ledger-flow/
  pom.xml                    parent - dependencyManagement/pluginManagement only
  ledgerflow-contracts/      shared library: Kafka event types, no Spring deps
  payment-service/           owns payments, publishes to Kafka
  ledger-service/            consumes from Kafka, posts ledger entries
  docker-compose.yml         local dev: 2 Postgres + Kafka (KRaft) + Kafdrop
  .github/workflows/maven.yml
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
  Postgres at `ledgerflow_payment` / `ledgerflow_ledger` (see each module's
  `src/main/resources/application.yml`). CI provisions both databases against a single
  `postgres:latest` service container.
- **Integration tests** (`*Test.java` under `events/`) use `@EmbeddedKafka` + an H2 in-memory
  DB, configured via each module's `src/test/resources/application.yml`. **Spring Boot loads
  whichever `application.yml` it finds first on the classpath — it does not merge main and
  test config** — so the test YAML repeats everything the app needs (Kafka producer/consumer
  settings, datasource), not just the overrides. Forgetting this is the most common way a new
  test silently uses the wrong serializer/deserializer and fails confusingly.

`docker compose up` runs the full local stack (both services + their databases + Kafka +
Kafdrop UI at `localhost:9000`). Docker was not available in the environment these modules
were built in, so the compose file has been reviewed but not run end-to-end — verify it
yourself before trusting it blindly.

## Module boundaries

- **ledgerflow-contracts** — `PaymentCompletedEvent` and `EntryType`. Only dependency: Lombok
  (provided). Nothing here should ever depend on Spring, JPA, or either service.
- **payment-service** — owns the `payments` table and the `/api/v1/payments` REST API. Is the
  only Kafka *producer* in this system.
- **ledger-service** — owns `ledger_entries`, `processed_events`, `failed_events`. Is the only
  Kafka *consumer*. Has no public REST API beyond Actuator health.

Neither service's JPA layer reaches into the other's tables anymore (that used to happen via
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

## What's explicitly not done yet

- **Security is a wide-open placeholder.** Both services' `SecurityConfig` is
  `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` with CSRF disabled. This is
  not an oversight to "fix" opportunistically — it's a deliberately deferred milestone (JWT /
  OAuth2 resource server pattern). Don't add auth-adjacent code without checking spec.md first.
- **No Kubernetes manifests, no API gateway, no schema registry.** See spec.md's "Explicitly
  out of scope" section before assuming any of these should exist.

## Git workflow observed in this repo

Work has been landing as small, single-purpose PRs against `main` (module restructuring, then
infra, then tests, as separate reviewable diffs) rather than one large PR per milestone.
Follow that pattern for new work unless told otherwise: scope a branch to one coherent change,
verify it builds/tests standalone, then open a PR.
