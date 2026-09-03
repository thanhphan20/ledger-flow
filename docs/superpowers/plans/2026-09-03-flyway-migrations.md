# Flyway Migrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `ddl-auto: update` with Flyway across all three services (payment-service, ledger-service, loan-service) with migrations, `ddl-auto: validate`, and baseline-on-migrate.

**Architecture:** Each service gets a `V1__init.sql` matching its JPA entities, `ddl-auto: validate`, `baseline-on-migrate: true`. Tests run the same migrations on H2. Config parity: main yml + test yml (replaces main on test classpath). Baseline-on-migrate handles existing DBs; fresh DBs run V1.

**Tech Stack:** Spring Boot 4.0.5, Flyway 10.x/11.x (Boot BOM), PostgreSQL, H2 (MODE=PostgreSQL), Maven multi-module.

---

## File Structure Map

```
payment-service/
  pom.xml                                  (add flyway-core + flyway-database-postgresql)
  src/main/resources/
    application.yml                         (ddl-auto: validate, flyway baseline-on-migrate)
    db/migration/V1__init.sql               (payments table + unique index)
  src/test/resources/application.yml        (same flyway + validate, repeats all config)

ledger-service/
  pom.xml                                  (add flyway-core + flyway-database-postgresql)
  src/main/resources/
    application.yml                         (ddl-auto: validate, flyway baseline-on-migrate)
    db/migration/V1__init.sql               (ledger_entries, processed_events, failed_events)
  src/test/resources/application.yml        (same flyway + validate)

loan-service/
  pom.xml                                  (add flyway-core + flyway-database-postgresql)
  src/main/resources/
    application.yml                         (ddl-auto: validate, flyway baseline-on-migrate)
    db/migration/V1__init.sql               (loans table)
  src/test/resources/application.yml        (same flyway + validate)
```

---

## Task 1: payment-service — Flyway deps + config

**Files:**
- Modify: `payment-service/pom.xml`
- Modify: `payment-service/src/main/resources/application.yml`
- Modify: `payment-service/src/test/resources/application.yml`

- [ ] **Step 1: Add Flyway dependencies to pom.xml**

```xml
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
```
Place in `<dependencies>` after `spring-boot-kafka` (around line 55), keeping comment style.

- [ ] **Step 2: Run compile to verify deps resolve**

Run: `./mvnw -pl payment-service -am compile -B`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Update main application.yml**

```yaml
server:
  port: 8081

spring:
  application:
    name: payment-service

  datasource:
    url: jdbc:postgresql://localhost:5432/ledgerflow_payment
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    properties:
      spring.json.add.type.headers: false

  flyway:
    baseline-on-migrate: true

management:
  endpoints:
    web:
      exposure:
        include: health

jwt:
  secret: ${JWT_SECRET}
```

Only changes: `ddl-auto: validate` and `flyway:` block added.

- [ ] **Step 4: Update test application.yml (replaces main entirely)**

```yaml
spring:
  # This file replaces src/main/resources/application.yml entirely on the test classpath (Spring
  # Boot loads whichever application.yml it finds first, it does not merge the two), so anything
  # the app needs at runtime has to be repeated here rather than just listing overrides.
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    properties:
      spring.json.add.type.headers: false

  datasource:
    url: jdbc:h2:mem:paymenttest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

  flyway:
    baseline-on-migrate: true

# Same dev-only value as src/main/resources/application.yml - this file replaces main config
# entirely on the test classpath, so it must be repeated here or JwtConfig's beans fail to
# construct and every @SpringBootTest in this module fails to start its context.
jwt:
  secret: dev-only-insecure-jwt-signing-key-change-me-1234567890
```

Changes: `ddl-auto: validate`, `flyway: baseline-on-migrate: true`. All other keys identical to current test yml.

- [ ] **Step 5: Run tests to verify config (no migration yet — expect boot failure)**

Run: `./mvnw -pl payment-service test -B`
Expected: FAIL — `Validate` will fail because no migration yet. This is the red phase for next task.

- [ ] **Step 6: Commit**

```bash
./mvnw spotless:apply
git add payment-service/pom.xml payment-service/src/main/resources/application.yml payment-service/src/test/resources/application.yml
git commit -m "feat(payment-service): add Flyway config"
```

---

## Task 2: payment-service — V1__init.sql migration

**Files:**
- Create: `payment-service/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: Write the failing test (run existing suite — should fail Validate)**

Run: `./mvnw -pl payment-service test -B`
Expected: FAIL with `ValidationException: Migration V1__init.sql not found` or similar.

- [ ] **Step 2: Create V1__init.sql (derive from Hibernate's schema)**

```sql
-- payment-service V1__init.sql
CREATE TABLE payments (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id BIGINT,
  amount NUMERIC(38, 2),
  currency VARCHAR(255),
  status VARCHAR(255),
  reference_id VARCHAR(255),
  idempotency_key VARCHAR(255),
  created_at TIMESTAMP
);
CREATE UNIQUE INDEX uk_payments_idempotency_key ON payments (idempotency_key);
```

Match Hibernate's DDL exactly: numeric(38,2) for BigDecimal, identity for id, unique index for @Column(unique=true) on idempotency_key.

- [ ] **Step 3: Run tests — expect PASS**

Run: `./mvnw -pl payment-service test -B`
Expected: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
./mvnw spotless:apply
git add payment-service/src/main/resources/db/migration/V1__init.sql
git commit -m "feat(payment-service): add V1 migration"
```

---

## Task 3: ledger-service — Flyway deps + config

**Files:**
- Modify: `ledger-service/pom.xml`
- Modify: `ledger-service/src/main/resources/application.yml`
- Modify: `ledger-service/src/test/resources/application.yml`

- [ ] **Step 1: Add Flyway dependencies to pom.xml** (mirror payment's)

```xml
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
```

- [ ] **Step 2: Update main application.yml** — change `ddl-auto: update` to `validate`, add `flyway: baseline-on-migrate: true`. Keep existing kafka consumer config (group-id, error handling deserializer, etc.) unchanged.

- [ ] **Step 3: Update test application.yml** — repeat flyway config + validate. Keep existing H2 datasource + kafka consumer + kafka error handling config + H2 dialect.

- [ ] **Step 4: Compile and commit**

Run: `./mvnw -pl ledger-service -am compile -B` → BUILD SUCCESS
```bash
./mvnw spotless:apply
git add ledger-service/pom.xml ledger-service/src/main/resources/application.yml ledger-service/src/test/resources/application.yml
git commit -m "feat(ledger-service): add Flyway config"
```

---

## Task 4: ledger-service — V1__init.sql migration (3 tables)

**Files:**
- Create: `ledger-service/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: Red phase — run tests, expect Validate failure**

Run: `./mvnw -pl ledger-service test -B` → FAIL (migration not found)

- [ ] **Step 2: Create V1__init.sql with 3 tables**

```sql
-- ledger-service V1__init.sql
CREATE TABLE ledger_entries (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  account_id BIGINT,
  payment_id BIGINT,
  loan_id BIGINT,
  amount NUMERIC(38, 2),
  currency VARCHAR(255),
  type VARCHAR(255),
  created_at TIMESTAMP
);

CREATE TABLE processed_events (
  event_id VARCHAR(255) PRIMARY KEY,
  processed_at TIMESTAMP
);

CREATE TABLE failed_events (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  event_id VARCHAR(255),
  event_type VARCHAR(255),
  payload TEXT,
  error_message VARCHAR(255),
  created_at TIMESTAMP
);
```

Notes: `processed_events.event_id` is PK (matches `@Id` on ProcessedEvent with `Persistable<String>`). `failed_events.payload` is TEXT (maps `@Lob` on String). `ledger_entries.loan_id` nullable (matches entity).

- [ ] **Step 3: Run tests — expect PASS**

Run: `./mvnw -pl ledger-service test -B` → Tests run: 3 (2 idempotency + smoke), 0 failures.

- [ ] **Step 4: Commit**

```bash
./mvnw spotless:apply
git add ledger-service/src/main/resources/db/migration/V1__init.sql
git commit -m "feat(ledger-service): add V1 migration"
```

---

## Task 5: loan-service — Flyway deps + config

**Files:**
- Modify: `loan-service/pom.xml`
- Modify: `loan-service/src/main/resources/application.yml`
- Modify: `loan-service/src/test/resources/application.yml`

(Loan-service only exists on `feat/loan-service` branch; ensure you're on `feat/loan-service` or a branch forked from it.)

- [ ] **Step 1: Add Flyway dependencies to pom.xml** (mirror payment's)

```xml
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
```

- [ ] **Step 2: Update main application.yml** — change `ddl-auto: update` → `validate`, add `flyway: baseline-on-migrate: true`. Keep existing port 8083, datasource ledgerflow_loan, kafka producer, jwt.secret.

- [ ] **Step 3: Update test application.yml** — repeat flyway + validate, keep H2 datasource, embedded kafka, jwt secret.

- [ ] **Step 4: Compile and commit**

Run: `./mvnw -pl loan-service -am compile -B` → BUILD SUCCESS
```bash
./mvnw spotless:apply
git add loan-service/pom.xml loan-service/src/main/resources/application.yml loan-service/src/test/resources/application.yml
git commit -m "feat(loan-service): add Flyway config"
```

---

## Task 6: loan-service — V1__init.sql migration

**Files:**
- Create: `loan-service/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: Red phase — run tests, expect Validate failure**

Run: `./mvnw -pl loan-service -am test -B` → FAIL

- [ ] **Step 2: Create V1__init.sql**

```sql
-- loan-service V1__init.sql
CREATE TABLE loans (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id BIGINT,
  amount NUMERIC(38, 2),
  currency VARCHAR(255),
  term_months INTEGER,
  status VARCHAR(255),
  created_at TIMESTAMP
);
```

- [ ] **Step 3: Run tests — expect PASS**

Run: `./mvnw -pl loan-service -am test -B` → Tests run: 5 (LoanServiceApproval 3, LoanEventKafkaBridge 1, smoke 1), 0 failures.

- [ ] **Step 4: Commit**

```bash
./mvnw spotless:apply
git add loan-service/src/main/resources/db/migration/V1__init.sql
git commit -m "feat(loan-service): add V1 migration"
```

---

## Task 7: Verify baseline works on existing DBs (manual smoke)

**Files:** none (verification only)

- [ ] **Step 1: Start payment-service against existing local Postgres (ledgerflow_payment with ddl-auto tables)**

Run: `java -jar payment-service/target/payment-service-*.jar --spring.profiles.active=local`
Expected: Boot succeeds, Flyway baselines at version 1, Validate passes.

- [ ] **Step 2: Verify ledger-service similarly**

Run: `java -jar ledger-service/target/ledger-service-*.jar` (or via compose)
Expected: Boot succeeds, Flyway baselines, Validate passes.

- [ ] **Step 3: Report outcome**

If Validate fails (schema mismatch), V1 must be adjusted to match Hibernate's actual output. Document the exact mismatch in a follow-up.

- [ ] **Step 5: Commit no-op or fix if needed**

```bash
git commit --allow-empty -m "chore: baseline verification complete" || true
```

---

## Task 8: Full reactor build + spotless check

**Files:** none (verification)

- [ ] **Step 1: Full build**

Run: `./mvnw clean spotless:apply install -B`
Expected: BUILD SUCCESS, all 5 modules, 14 tests, spotless clean.

- [ ] **Step 2: Commit no-op**

```bash
git commit --allow-empty -m "chore: full reactor build verified"
```

---

## Task 9: Verify test yml Flyway behavior on fresh H2 (no baseline)

- [ ] **Step 1: Run loan-service tests with fresh H2 (no prior state)**

Run: `./mvnw -pl loan-service test -B`
Expected: 5 tests pass. Flyway runs V1 on empty H2, creates tables, tests pass.

- [ ] **Step 2: Run ledger-service tests (both idempotency tests)**

Run: `./mvnw -pl ledger-service test -B`
Expected: 3 tests pass. Both idempotency tests exercise 23505 skip on PK/unique.

- [ ] **Step 3: Run payment-service tests**

Run: `./mvnw -pl payment-service test -B`
Expected: 7 tests pass.

- [ ] **Step 6: Commit**

```bash
git commit --allow-empty -m "chore: H2 test migration verification complete"
```

---

## Task 10: Full reactor build + docs update

- [ ] **Step 1: Final full build**

Run: `./mvnw clean spotless:apply install -B`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Update README if any compose/k8s notes changed** (spec says no CI/compose/k8s changes needed — Flyway runs on boot; verify no doc updates needed).

- [ ] **Step 3: Final commit**

```bash
./mvnw spotless:apply
git add -A
git commit -m "feat: adopt Flyway migrations across all services"
```

---

## Self-Review Checklist (before offering execution)

- [ ] Spec coverage: all spec sections mapped to tasks (config, migrations, baseline, tests, landing)
- [ ] No placeholders — every step has exact SQL, YAML, XML, commands
- [ ] Type consistency: numeric(38,2) used everywhere, IDENTITY identity columns, TEXT for @Lob
- [ ] TDD order: each migration task runs red (Validate fails) then green (after V1 created)
- [ ] Commit hygiene: per-task commits with conventional messages
- [ ] Test yml caveat documented (replaces main, full config repeated)
- [ ] Baseline logic explained (existing DBs baselined, fresh DBs run V1)
- [ ] Landing: single branch `feat/flyway-migrations` off `feat/loan-service`

**Plan complete and saved to `docs/superpowers/plans/2026-09-03-flyway-migrations.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**