# 💸 LedgerFlow

**LedgerFlow** is a Java/Spring Boot learning project: a payment-and-ledger system that started
as a monolith and has since been split into two independently-deployable microservices —
`payment-service` and `ledger-service` — communicating only through Kafka. See
[spec.md](spec.md) for the full system design and [AGENTS.md](AGENTS.md) for repo conventions.

[![Maven CI](https://github.com/thanhphan20/ledger-flow/actions/workflows/maven.yml/badge.svg)](https://github.com/thanhphan20/ledger-flow/actions/workflows/maven.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 🚀 Key Features

- **Two decoupled microservices**, not a modular monolith: `payment-service` and
  `ledger-service` run as separate processes with separate databases and talk to each other
  only through Kafka — no shared JVM, no shared database, no synchronous calls between them.
- **Kafka event-driven integration**: `payment-service` publishes `PaymentCompletedEvent` to
  the `payment.completed` topic after its transaction commits; `ledger-service` consumes it
  and posts a ledger entry.
- **Idempotent, at-least-once processing**: duplicate/redelivered events are detected and
  safely no-op'd via a `ProcessedEvent` guard, so retries and reconciliation republishes can
  never double-post a ledger entry.
- **Kafka-native retry/DLQ**: failed messages retry with backoff, then land in a
  `failed_events` table instead of crashing the consumer.
- **Local dev via Docker Compose**: two Postgres instances, a single-node Kafka broker
  (KRaft mode, no Zookeeper), and a Kafdrop UI for inspecting topics.
- **Automated formatting**: Spotless + Google Java Format, enforced in `mvn verify`.

---

## 🛠️ Technology Stack

- **Core**: Java 21 (LTS)
- **Framework**: Spring Boot 4.0.5
- **Messaging**: Apache Kafka (via spring-kafka)
- **Database**: PostgreSQL (one instance per service)
- **Persistence**: Spring Data JPA / Hibernate
- **Tooling**: Maven (multi-module), Spotless, Lombok, Docker Compose, GitHub Actions

---

## 🏗️ Architecture Overview

```
ledger-flow/
  pom.xml                  parent (Maven multi-module, no source of its own)
  ledgerflow-contracts/    shared Kafka event types (PaymentCompletedEvent, EntryType)
  payment-service/         owns `payments`; only Kafka producer; REST API
  ledger-service/          owns ledger tables; only Kafka consumer; no public REST API
  docker-compose.yml       local dev stack
```

1. `POST /api/v1/payments` on **payment-service** creates a `Payment` and commits it.
2. After commit, `payment-service` publishes a `PaymentCompletedEvent` to the Kafka topic
   `payment.completed`.
3. **ledger-service** consumes it, checks a `ProcessedEvent` idempotency guard, and creates a
   `LedgerEntry`.
4. A scheduled reconciliation job in `payment-service` republishes stale `COMPLETED` payments
   as a safety net — safe because of ledger-service's idempotency guard.

Full detail, including the producer/consumer guarantees and what's deliberately out of scope
(JWT auth, API gateway, Kubernetes), is in [spec.md](spec.md).

---

## 🏁 Getting Started

### Prerequisites

- **Java 21**
- **Maven 3.9+** (or use the bundled `./mvnw`)
- **Docker + Docker Compose** (for the easiest local run)

### Option A: Run everything with Docker Compose (recommended)

```bash
git clone https://github.com/thanhphan20/ledger-flow.git
cd ledger-flow
docker compose up --build
```

This starts both Postgres databases, a Kafka broker, Kafdrop (`http://localhost:9000`),
`payment-service` (`http://localhost:8081`), and `ledger-service` (`http://localhost:8082`).

Try it:

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "amount": 42.50, "currency": "USD", "idempotencyKey": "demo-001"}'
```

Watch `payment.completed` in Kafdrop, or check `ledger-service`'s logs — a `LedgerEntry`
should appear within a second or two.

### Option B: Run natively (no Docker)

You'll need your own Postgres instance and Kafka broker (standalone, KRaft mode is fine).
Create two databases, `ledgerflow_payment` and `ledgerflow_ledger`, matching each service's
`src/main/resources/application.yml`, then:

```bash
./mvnw clean install                                  # builds & tests all 3 modules
java -jar payment-service/target/payment-service-*.jar &
java -jar ledger-service/target/ledger-service-*.jar &
```

---

## 🧪 Building & Testing

```bash
./mvnw clean install              # build + test all modules (reactor build)
./mvnw test                       # tests only
./mvnw -pl payment-service test   # a single module
./mvnw spotless:apply             # auto-format before committing
```

Smoke tests need real Postgres databases (`ledgerflow_payment`, `ledgerflow_ledger`);
Kafka-boundary integration tests use `@EmbeddedKafka` + H2, no external services required.
See [AGENTS.md](AGENTS.md) for the details (including a config-loading gotcha worth knowing
before you add a new test).

---

## 🤖 CI/CD

GitHub Actions (`.github/workflows/maven.yml`) runs on every push/PR to `main`:
1. Builds and tests all three Maven modules against real Postgres databases.
2. Builds both service Docker images and brings up the full `docker-compose.yml` stack.
3. Posts a real payment through it and confirms the event reaches `ledger-service` and lands
   in Postgres — end-to-end verification of the Kafka wiring and the Dockerfiles, not just
   the JVM-level tests.

To manually format code: `./mvnw spotless:apply`

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Developed with ❤️ by [thanhphan20](https://github.com/thanhphan20)
