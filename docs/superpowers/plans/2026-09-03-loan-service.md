# Loan Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `loan-service` as a third microservice: REST + JWT + own DB, publishing `LoanApprovedEvent` to a new `loan.approved` topic that ledger-service consumes into `ledger_entries`.

**Architecture:** Mirrors payment-service (same layout, same JWT pattern, same AFTER_COMMIT Kafka bridge). ledger-service gets a second `@KafkaListener` with its own consumer factory because the yml-pinned default deserializer type (`PaymentCompletedEvent`) is wrong for the new topic. Spec: `docs/superpowers/specs/2026-09-03-loan-service-design.md`.

**Tech Stack:** Java 21, Spring Boot 4.0.5, spring-kafka (JsonSerializer/JsonDeserializer, no type headers), PostgreSQL, H2 + `@EmbeddedKafka` for tests, Lombok, Spotless (Google Java Format — run `./mvnw spotless:apply` before every commit).

**Ports:** loan-service `:8083`, loan-db host port `:5434`. **Topic:** `loan.approved`, keyed by `loanId` string.

**Reactor builds:** always build with `-am` when touching contracts (`./mvnw -pl loan-service -am test`). Full build: `./mvnw clean install`.

---

## PR 1: contracts + loan-service

### Task 1: `LoanApprovedEvent` in ledgerflow-contracts

**Files:**
- Create: `ledgerflow-contracts/src/main/java/com/ledgerflow/contracts/events/LoanApprovedEvent.java`

- [ ] **Step 1: Write the event class**

```java
package com.ledgerflow.contracts.events;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanApprovedEvent {
  private String eventId;
  private Long loanId;
  private Long userId;
  private BigDecimal amount;
  private String currency;
}
```

(No `EntryType` field — loan entries are always `CREDIT`, decided in the ledger processor.)

- [ ] **Step 2: Build contracts and install to local repo**

Run: `./mvnw -pl ledgerflow-contracts clean install -DskipTests -B`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
./mvnw spotless:apply
git add ledgerflow-contracts/src/main/java/com/ledgerflow/contracts/events/LoanApprovedEvent.java
git commit -m "feat(contracts): add LoanApprovedEvent"
```

---

### Task 2: loan-service module skeleton

**Files:**
- Modify: `pom.xml` (root — `<modules>` list)
- Create: `loan-service/pom.xml`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/LoanServiceApplication.java`
- Create: `loan-service/src/main/resources/application.yml`
- Create: `loan-service/src/test/resources/application.yml`

- [ ] **Step 1: Register the module in the root pom**

In `pom.xml`, change:

```xml
	<modules>
		<module>ledgerflow-contracts</module>
		<module>payment-service</module>
		<module>ledger-service</module>
		<module>loan-service</module>
	</modules>
```

- [ ] **Step 2: Create `loan-service/pom.xml`** (copy of payment-service's, with artifact renamed)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.ledgerflow</groupId>
		<artifactId>ledgerflow-parent</artifactId>
		<version>0.0.1-SNAPSHOT</version>
	</parent>

	<artifactId>loan-service</artifactId>
	<packaging>jar</packaging>
	<name>loan-service</name>
	<description>Loan Service - owns loans and publishes loan-approved events to Kafka</description>

	<dependencies>
		<dependency>
			<groupId>com.ledgerflow</groupId>
			<artifactId>ledgerflow-contracts</artifactId>
			<version>${project.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.kafka</groupId>
			<artifactId>spring-kafka</artifactId>
		</dependency>
		<!-- Boot 4 moved Kafka autoconfiguration (KafkaTemplate bean, spring.kafka.* properties)
		     out of spring-boot-autoconfigure into its own module. -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-kafka</artifactId>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>2.5.0</version>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.kafka</groupId>
			<artifactId>spring-kafka-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>test</scope>
		</dependency>
		<!-- TestRestTemplate's autoconfiguration (spring-boot-resttestclient, pulled in transitively
		     via spring-boot-starter-webmvc-test) needs RestTemplateBuilder, which Boot 4 split into
		     its own module rather than shipping inside spring-boot-autoconfigure. -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-restclient</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>com.diffplug.spotless</groupId>
				<artifactId>spotless-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

- [ ] **Step 3: Create `LoanServiceApplication.java`** (no `@EnableScheduling` — no reconciliation job here)

```java
package com.ledgerflow.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoanServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(LoanServiceApplication.class, args);
  }
}
```

- [ ] **Step 4: Create `loan-service/src/main/resources/application.yml`**

```yaml
server:
  port: 8083

spring:
  application:
    name: loan-service

  datasource:
    url: jdbc:postgresql://localhost:5432/ledgerflow_loan
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
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

management:
  endpoints:
    web:
      exposure:
        include: health

# Same fails-closed rule as payment-service: no checked-in default key. See
# payment-service/src/main/resources/application.yml for the full rationale.
jwt:
  secret: ${JWT_SECRET}
```

- [ ] **Step 5: Create `loan-service/src/test/resources/application.yml`** — remember: this file REPLACES main config on the test classpath, so everything must be repeated

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
    url: jdbc:h2:mem:loantest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

# Same dev-only value as src/main/resources/application.yml - this file replaces main config
# entirely on the test classpath, so it must be repeated here or JwtConfig's beans fail to
# construct and every @SpringBootTest in this module fails to start its context.
jwt:
  secret: dev-only-insecure-jwt-signing-key-change-me-1234567890
```

- [ ] **Step 6: Compile the module**

Run: `./mvnw -pl loan-service -am compile -B`
Expected: `BUILD SUCCESS` (application.yml's `${JWT_SECRET}` is a runtime failure, not a compile one)

- [ ] **Step 7: Commit**

```bash
./mvnw spotless:apply
git add pom.xml loan-service
git commit -m "feat(loan-service): add module skeleton"
```

---

### Task 3: `Loan` entity, `LoanStatus`, repository

**Files:**
- Create: `loan-service/src/main/java/com/ledgerflow/loan/entities/Loan.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/enums/LoanStatus.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/repositories/LoanRepository.java`

- [ ] **Step 1: Create the enum**

```java
package com.ledgerflow.loan.enums;

public enum LoanStatus {
  PENDING,
  APPROVED
}
```

- [ ] **Step 2: Create the entity**

```java
package com.ledgerflow.loan.entities;

import com.ledgerflow.loan.enums.LoanStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  private BigDecimal amount;

  private String currency;

  private Integer termMonths;

  @Enumerated(EnumType.STRING)
  private LoanStatus status;

  private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create the repository**

```java
package com.ledgerflow.loan.repositories;

import com.ledgerflow.loan.entities.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {}
```

- [ ] **Step 4: Compile**

Run: `./mvnw -pl loan-service -am compile -B`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply
git add loan-service/src/main/java/com/ledgerflow/loan
git commit -m "feat(loan-service): add Loan entity, status enum, repository"
```

---

### Task 4: DTOs

**Files:**
- Create: `loan-service/src/main/java/com/ledgerflow/loan/dtos/CreateLoanRequest.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/dtos/LoanResponse.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/dtos/LoginRequest.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/dtos/LoginResponse.java`

- [ ] **Step 1: Create `CreateLoanRequest.java`**

```java
package com.ledgerflow.loan.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoanRequest {
  @NotNull private Long userId;

  @NotNull
  @DecimalMin(value = "0.01")
  private BigDecimal amount;

  @NotBlank private String currency;

  @NotNull
  @Positive
  private Integer termMonths;
}
```

- [ ] **Step 2: Create `LoanResponse.java`**

```java
package com.ledgerflow.loan.dtos;

import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanResponse {
  private Long id;
  private Long userId;
  private BigDecimal amount;
  private String currency;
  private Integer termMonths;
  private LoanStatus status;
  private LocalDateTime createdAt;

  public static LoanResponse from(Loan loan) {
    return LoanResponse.builder()
        .id(loan.getId())
        .userId(loan.getUserId())
        .amount(loan.getAmount())
        .currency(loan.getCurrency())
        .termMonths(loan.getTermMonths())
        .status(loan.getStatus())
        .createdAt(loan.getCreatedAt())
        .build();
  }
}
```

- [ ] **Step 3: Create `LoginRequest.java`** (verbatim copy of payment-service's, package changed)

```java
package com.ledgerflow.loan.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
  @NotBlank private String username;
  @NotBlank private String password;
}
```

- [ ] **Step 4: Create `LoginResponse.java`** (verbatim copy, package changed)

```java
package com.ledgerflow.loan.dtos;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
  private String token;
  private Instant expiresAt;
}
```

- [ ] **Step 5: Compile and commit**

Run: `./mvnw -pl loan-service -am compile -B` → expect `BUILD SUCCESS`

```bash
./mvnw spotless:apply
git add loan-service/src/main/java/com/ledgerflow/loan/dtos
git commit -m "feat(loan-service): add request/response DTOs"
```

---

### Task 5: JWT auth — `JwtConfig`, `SecurityConfig`, `AuthController`

**Files:**
- Create: `loan-service/src/main/java/com/ledgerflow/loan/config/JwtConfig.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/config/SecurityConfig.java`
- Create: `loan-service/src/main/java/com/ledgerflow/loan/controllers/AuthController.java`

Copy payment-service's three classes verbatim, changing only the package (`com.ledgerflow.payment.*` → `com.ledgerflow.loan.*`) and the JWT issuer string. The classes are shown in full so no cross-referencing is needed:

- [ ] **Step 1: Create `JwtConfig.java`** — exact copy of
      `payment-service/src/main/java/com/ledgerflow/payment/config/JwtConfig.java` with package
      `com.ledgerflow.loan.config`. Beans: `jwtSecretKey` (HmacSHA256 from `${jwt.secret}`),
      `jwtEncoder`, `jwtDecoder` (HS256), `passwordEncoder` (BCrypt), `userDetailsService`
      (in-memory `demo`/`demo-password`/`ROLE_USER` via `InMemoryUserDetailsManager`),
      `authenticationManager`.

- [ ] **Step 2: Create `SecurityConfig.java`** — exact copy of payment's with package
      `com.ledgerflow.loan.config`: stateless, CSRF off, `requestMatchers("/api/v1/auth/login",
      "/actuator/health", "/error").permitAll()`, `anyRequest().authenticated()`,
      `oauth2ResourceServer(jwt)`, frame options disabled.

- [ ] **Step 3: Create `AuthController.java`** — copy of payment's with package
      `com.ledgerflow.loan.controllers` and ONE functional change: `.issuer("payment-service")`
      becomes `.issuer("loan-service")`. Everything else identical: `POST /api/v1/auth/login`,
      401 on bad credentials, 1-hour token, `roles: ROLE_USER` claim, returns `LoginResponse`.

- [ ] **Step 4: Verify the security wiring boots** — the smoke test in Task 8 covers this; here just compile.

Run: `./mvnw -pl loan-service -am compile -B` → expect `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply
git add loan-service/src/main/java/com/ledgerflow/loan/config loan-service/src/main/java/com/ledgerflow/loan/controllers/AuthController.java
git commit -m "feat(loan-service): add JWT resource server auth"
```

---

### Task 6: `LoanService` — create, approve, get (TDD)

**Files:**
- Create: `loan-service/src/main/java/com/ledgerflow/loan/services/LoanService.java`
- Test: `loan-service/src/test/java/com/ledgerflow/loan/services/LoanServiceApprovalTest.java`

The event payload itself is asserted end-to-end over Kafka in Task 8's bridge test; this test
pins the status transitions and error codes. `@EmbeddedKafka` is on the class so the
AFTER_COMMIT publish from the first approve fires harmlessly against the embedded broker.

- [ ] **Step 1: Write the failing test** (404 unknown id, 409 double-approve, status set on approve)

```java
package com.ledgerflow.loan.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.loan.enums.LoanStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "loan.approved")
class LoanServiceApprovalTest {

  @Autowired private LoanService loanService;

  private Long createPendingLoan() {
    return loanService.createLoan(7L, new BigDecimal("1000.00"), "USD", 12).getId();
  }

  @Test
  void approveUnknownLoanReturns404() {
    assertThatThrownBy(() -> loanService.approveLoan(999_999L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void doubleApproveReturns409() {
    Long id = createPendingLoan();
    loanService.approveLoan(id);

    assertThatThrownBy(() -> loanService.approveLoan(id))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void approveSetsApprovedStatus() {
    Long id = createPendingLoan();

    Loan approved = loanService.approveLoan(id);

    assertThat(approved.getStatus()).isEqualTo(LoanStatus.APPROVED);
    assertThat(approved.getId()).isEqualTo(id);
  }
}
```

- [ ] **Step 2: Run it — expect compile failure**

Run: `./mvnw -pl loan-service test -Dtest=LoanServiceApprovalTest -B`
Expected: FAIL — `LoanService` does not exist

- [ ] **Step 3: Implement `LoanService.java`**

```java
package com.ledgerflow.loan.services;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.enums.LoanStatus;
import com.ledgerflow.loan.repositories.LoanRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LoanService {

  private final ApplicationEventPublisher eventPublisher;
  private final LoanRepository loanRepository;

  @Transactional
  public Loan createLoan(Long userId, BigDecimal amount, String currency, Integer termMonths) {
    Loan loan =
        Loan.builder()
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .termMonths(termMonths)
            .status(LoanStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
    return loanRepository.save(loan);
  }

  @Transactional
  public Loan approveLoan(Long loanId) {
    Loan loan =
        loanRepository
            .findById(loanId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
    if (loan.getStatus() == LoanStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Loan already approved");
    }
    loan.setStatus(LoanStatus.APPROVED);
    loan = loanRepository.save(loan);

    eventPublisher.publishEvent(
        new LoanApprovedEvent(
            UUID.randomUUID().toString(),
            loan.getId(),
            loan.getUserId(),
            loan.getAmount(),
            loan.getCurrency()));
    return loan;
  }

  public Loan getLoan(Long loanId) {
    return loanRepository
        .findById(loanId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
  }
}
```

- [ ] **Step 4: Run the test — expect PASS**

Run: `./mvnw -pl loan-service test -Dtest=LoanServiceApprovalTest -B`
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply
git add loan-service/src
git commit -m "feat(loan-service): add LoanService with approve flow"
```

---

### Task 7: `LoanController`

**Files:**
- Create: `loan-service/src/main/java/com/ledgerflow/loan/controllers/LoanController.java`

- [ ] **Step 1: Create the controller**

```java
package com.ledgerflow.loan.controllers;

import com.ledgerflow.loan.dtos.CreateLoanRequest;
import com.ledgerflow.loan.dtos.LoanResponse;
import com.ledgerflow.loan.entities.Loan;
import com.ledgerflow.loan.services.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loan", description = "Endpoints for managing loans")
public class LoanController {

  private final LoanService loanService;

  @PostMapping
  @Operation(summary = "Create a new loan application")
  @ApiResponse(responseCode = "200", description = "Loan created in PENDING state")
  public ResponseEntity<LoanResponse> createLoan(@Valid @RequestBody CreateLoanRequest request) {
    Loan loan =
        loanService.createLoan(
            request.getUserId(),
            request.getAmount(),
            request.getCurrency(),
            request.getTermMonths());
    return ResponseEntity.ok(LoanResponse.from(loan));
  }

  @PostMapping("/{id}/approve")
  @Operation(summary = "Approve a pending loan and publish LoanApprovedEvent")
  @ApiResponse(responseCode = "200", description = "Loan approved")
  @ApiResponse(responseCode = "409", description = "Loan already approved")
  public ResponseEntity<LoanResponse> approveLoan(@PathVariable Long id) {
    return ResponseEntity.ok(LoanResponse.from(loanService.approveLoan(id)));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch a loan by id")
  @ApiResponse(responseCode = "200", description = "Loan found")
  public ResponseEntity<LoanResponse> getLoan(@PathVariable Long id) {
    return ResponseEntity.ok(LoanResponse.from(loanService.getLoan(id)));
  }
}
```

- [ ] **Step 2: Compile**

Run: `./mvnw -pl loan-service -am compile -B` → expect `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
./mvnw spotless:apply
git add loan-service/src/main/java/com/ledgerflow/loan/controllers/LoanController.java
git commit -m "feat(loan-service): add LoanController"
```

---

### Task 8: `LoanEventKafkaBridge` + integration test (TDD)

**Files:**
- Create: `loan-service/src/main/java/com/ledgerflow/loan/events/LoanEventKafkaBridge.java`
- Test: `loan-service/src/test/java/com/ledgerflow/loan/events/LoanEventKafkaBridgeTest.java`

- [ ] **Step 1: Write the failing test** — mirrors `PaymentEventKafkaBridgeTest`

```java
package com.ledgerflow.loan.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.loan.enums.LoanStatus;
import com.ledgerflow.loan.services.LoanService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Verifies {@link LoanEventKafkaBridge} publishes a loan's approved event to Kafka after the
 * approving transaction commits.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "loan.approved")
class LoanEventKafkaBridgeTest {

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private LoanService loanService;

  @Test
  void publishesToKafkaAfterLoanApprovalCommits() {
    Long loanId =
        loanService.createLoan(7L, new BigDecimal("500.00"), "USD", 12).getId();

    Loan approved = loanService.approveLoan(loanId);
    assertThat(approved.getStatus()).isEqualTo(LoanStatus.APPROVED);

    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(
            "test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
    consumerProps.put(
        "key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class);
    consumerProps.put("value.deserializer", ErrorHandlingDeserializer.class);
    consumerProps.put("spring.deserializer.value.delegate.class", JsonDeserializer.class);
    consumerProps.put("spring.json.trusted.packages", "com.ledgerflow.contracts.events");
    consumerProps.put("spring.json.use.type.headers", false);
    consumerProps.put(
        "spring.json.value.default.type", "com.ledgerflow.contracts.events.LoanApprovedEvent");

    DefaultKafkaConsumerFactory<String, LoanApprovedEvent> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);
    try (Consumer<String, LoanApprovedEvent> consumer = consumerFactory.createConsumer()) {
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "loan.approved");
      ConsumerRecord<String, LoanApprovedEvent> record =
          KafkaTestUtils.getSingleRecord(consumer, "loan.approved", Duration.ofSeconds(10));
      assertThat(record.key()).isEqualTo(loanId.toString());
      assertThat(record.value().getLoanId()).isEqualTo(loanId);
      assertThat(record.value().getAmount()).isEqualByComparingTo("500.00");
    }
  }
}
```

- [ ] **Step 2: Run it — expect compile failure** (`LoanEventKafkaBridge` missing; event never arrives)

Run: `./mvnw -pl loan-service test -Dtest=LoanEventKafkaBridgeTest -B`
Expected: FAIL

- [ ] **Step 3: Implement the bridge** — mirror of `PaymentEventKafkaBridge`

```java
package com.ledgerflow.loan.events;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process {@link LoanApprovedEvent} to Kafka AFTER_COMMIT, so ledger-service never
 * sees a loan approval that hasn't committed to the loan-service database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventKafkaBridge {

  private static final String TOPIC = "loan.approved";

  // Injected as <Object, Object> to match Spring Boot's auto-configured KafkaTemplate bean
  // exactly (generics are invariant); the JSON serializer configured in application.yml still
  // serializes the actual event correctly at runtime regardless of this compile-time type.
  private final KafkaTemplate<Object, Object> kafkaTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLoanApproved(LoanApprovedEvent event) {
    log.info("Publishing loan approved event {} to {}", event.getEventId(), TOPIC);
    kafkaTemplate.send(TOPIC, event.getLoanId().toString(), event);
  }
}
```

- [ ] **Step 4: Run the test — expect PASS**

Run: `./mvnw -pl loan-service test -Dtest=LoanEventKafkaBridgeTest -B`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply
git add loan-service/src
git commit -m "feat(loan-service): add Kafka bridge for loan approvals"
```

---

### Task 9: Smoke test + PR 1 verification

**Files:**
- Create: `loan-service/src/test/java/com/ledgerflow/loan/LoanServiceApplicationTests.java`
- Create: `loan-service/Dockerfile`

- [ ] **Step 1: Create the smoke test** — mirror of `PaymentServiceApplicationTests`

```java
package com.ledgerflow.loan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LoanServiceApplicationTests {

  @Test
  void contextLoads() {}
}
```

- [ ] **Step 2: Create `loan-service/Dockerfile`** — copy of payment's with names swapped

```dockerfile
# Build context must be the repo root (not this directory) so the reactor build can see the
# parent pom and the ledgerflow-contracts module this service depends on.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY ledgerflow-contracts ledgerflow-contracts
COPY payment-service payment-service
COPY ledger-service ledger-service
COPY loan-service loan-service

RUN chmod +x mvnw && ./mvnw -q -pl loan-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/loan-service/target/loan-service-*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Run the full loan-service suite**

Run: `./mvnw -pl loan-service -am test -B`
Expected: all green (`LoanServiceApprovalTest` 3, `LoanEventKafkaBridgeTest` 1, smoke 1). The smoke test boots against H2 via the test classpath — no local Postgres needed for this module's suite.

- [ ] **Step 4: Full reactor build to prove nothing else broke**

Run: `./mvnw clean install -B`
Expected: `BUILD SUCCESS`, all modules. (If payment/ledger smoke tests fail locally for missing real Postgres, note it — CI provisions them; the loan module must still pass its own suite.)

- [ ] **Step 5: Commit**

```bash
./mvnw spotless:apply
git add loan-service
git commit -m "feat(loan-service): add smoke test and Dockerfile"
```

---

## PR 2: ledger-service consumes `loan.approved`

### Task 10: `LedgerEntry.loanId` column

**Files:**
- Modify: `ledger-service/src/main/java/com/ledgerflow/ledger/entities/LedgerEntry.java`

- [ ] **Step 1: Add the field** — after `paymentId`:

```java
  private Long loanId;
```

(`ddl-auto: update` handles the schema change; no migration file exists in this repo.)

- [ ] **Step 2: Existing ledger suite stays green**

Run: `./mvnw -pl ledger-service test -B`
Expected: PASS (idempotency test untouched)

- [ ] **Step 3: Commit**

```bash
./mvnw spotless:apply
git add ledger-service/src/main/java/com/ledgerflow/ledger/entities/LedgerEntry.java
git commit -m "feat(ledger-service): add loanId column to LedgerEntry"
```

---

### Task 11: Loan listener, processor, error handling (TDD)

**Files:**
- Create: `ledger-service/src/main/java/com/ledgerflow/ledger/config/LoanKafkaListenerConfig.java`
- Modify: `ledger-service/src/main/java/com/ledgerflow/ledger/events/LedgerEventHandler.java`
- Modify: `ledger-service/src/main/java/com/ledgerflow/ledger/services/LedgerEventProcessor.java`
- Modify: `ledger-service/src/main/java/com/ledgerflow/ledger/events/FailedEventRecorder.java`
- Modify: `ledger-service/src/main/java/com/ledgerflow/ledger/config/KafkaErrorHandlingConfig.java`
- Test: `ledger-service/src/test/java/com/ledgerflow/ledger/events/LoanEventHandlerIdempotencyTest.java`

WHY a separate consumer factory: `application.yml` pins
`spring.json.value.default.type: PaymentCompletedEvent` on the auto-configured default factory.
The `loan.approved` listener would try to deserialize `LoanApprovedEvent` JSON into
`PaymentCompletedEvent` and fail. Spec explicitly rejects type-mapping headers, so the loan
listener gets its own factory with its own pinned default type. The payment listener keeps
using the yml-driven default factory, unchanged.

- [ ] **Step 1: Write the failing test** — mirror of `LedgerEventHandlerIdempotencyTest`

```java
package com.ledgerflow.ledger.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import com.ledgerflow.ledger.repositories.LedgerRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Verifies the idempotency guarantee holds for loan events: publishing the same LoanApprovedEvent
 * twice must only ever result in one CREDIT LedgerEntry row with loanId set.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"payment.completed", "loan.approved"})
class LoanEventHandlerIdempotencyTest {

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private LedgerRepository ledgerRepository;

  @Test
  void duplicateLoanEventOnlyProducesOneLedgerEntry() {
    LoanApprovedEvent event =
        new LoanApprovedEvent("test-loan-event-duplicate", 42L, 7L, new BigDecimal("500.00"), "USD");

    KafkaTemplate<String, LoanApprovedEvent> producer = testProducer();
    producer.send("loan.approved", event.getLoanId().toString(), event);
    producer.send("loan.approved", event.getLoanId().toString(), event);
    producer.flush();
    producer.destroy();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        ledgerRepository.findAll().stream()
                            .filter(entry -> entry.getLoanId() != null)
                            .filter(entry -> entry.getLoanId().equals(42L))
                            .count())
                    .isEqualTo(1));
  }

  private KafkaTemplate<String, LoanApprovedEvent> testProducer() {
    Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
    producerProps.put(
        "key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
    producerProps.put("value.serializer", JsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
  }
}
```

- [ ] **Step 2: Run it — expect compile failure** (no listener/processor for loans yet)

Run: `./mvnw -pl ledger-service test -Dtest=LoanEventHandlerIdempotencyTest -B`
Expected: FAIL

- [ ] **Step 3: Create `LoanKafkaListenerConfig.java`**

```java
package com.ledgerflow.ledger.config;

import com.ledgerflow.contracts.events.LoanApprovedEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * loan.approved needs its own consumer factory: the yml-driven default factory pins
 * spring.json.value.default.type to PaymentCompletedEvent, which is wrong for this topic.
 * No type headers on the wire (same contract decision as payment.completed), so the default
 * type is pinned here instead. Error handling and RECORD ack mirror the default factory's
 * yml settings.
 */
@Configuration
public class LoanKafkaListenerConfig {

  @Bean
  public ConsumerFactory<String, LoanApprovedEvent> loanConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "ledger-service");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.ledgerflow.contracts.events");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, LoanApprovedEvent.class);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, LoanApprovedEvent>
      loanListenerContainerFactory(
          ConsumerFactory<String, LoanApprovedEvent> loanConsumerFactory,
          DefaultErrorHandler kafkaErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, LoanApprovedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(loanConsumerFactory);
    factory.setCommonErrorHandler(kafkaErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }
}
```

- [ ] **Step 4: Add the processor method** — in `LedgerEventProcessor`, after `processPaymentCompleted`:

```java
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processLoanApproved(LoanApprovedEvent event) {
    try {
      processedEventRepository.saveAndFlush(
          ProcessedEvent.builder()
              .eventId(event.getEventId())
              .processedAt(LocalDateTime.now())
              .build());
    } catch (DataIntegrityViolationException ex) {
      if (!isDuplicateKeyViolation(ex)) {
        throw ex;
      }
      log.info("Ledger: skipping already processed loan event {}", event.getEventId());
      return;
    }

    LedgerEntry entry =
        LedgerEntry.builder()
            .accountId(event.getUserId())
            .loanId(event.getLoanId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .type(EntryType.CREDIT)
            .createdAt(LocalDateTime.now())
            .build();

    ledgerRepository.save(entry);
  }
```

Add import: `import com.ledgerflow.contracts.events.LoanApprovedEvent;`

- [ ] **Step 5: Add the listener method** — in `LedgerEventHandler`:

```java
  @KafkaListener(
      topics = "loan.approved",
      groupId = "ledger-service",
      containerFactory = "loanListenerContainerFactory")
  public void handleLoanApproved(LoanApprovedEvent event) {
    log.info("Handling loan approved event: {}", event.getEventId());
    ledgerEventProcessor.processLoanApproved(event);
  }
```

Add import: `import com.ledgerflow.contracts.events.LoanApprovedEvent;`

- [ ] **Step 6: Extend `FailedEventRecorder`** — add an overload next to the existing one and widen the serializer:

```java
  public void recordProcessingFailure(LoanApprovedEvent event, Exception error) {
    log.error(
        "Exhausted retries for loan event {}. Saving to failed_events.",
        event.getEventId(),
        error);
    save(
        event.getEventId(), event.getClass().getSimpleName(), serialize(event), error.getMessage());
  }
```

and change the private serializer signature from `serialize(PaymentCompletedEvent event)` to
`serialize(Object event)` (body unchanged).

- [ ] **Step 7: Extend `KafkaErrorHandlingConfig`** — the `else` branch becomes:

```java
              if (record.value() instanceof PaymentCompletedEvent event) {
                failedEventRecorder.recordProcessingFailure(event, exception);
              } else if (record.value() instanceof LoanApprovedEvent loanEvent) {
                failedEventRecorder.recordProcessingFailure(loanEvent, exception);
              } else {
                String description =
                    "%s-partition%d-offset%d"
                        .formatted(record.topic(), record.partition(), record.offset());
                failedEventRecorder.recordDeserializationFailure(description, exception);
              }
```

Add import: `import com.ledgerflow.contracts.events.LoanApprovedEvent;`

- [ ] **Step 8: Run the new test AND the full ledger suite**

Run: `./mvnw -pl ledger-service test -B`
Expected: `LoanEventHandlerIdempotencyTest` PASS, existing `LedgerEventHandlerIdempotencyTest` still PASS

- [ ] **Step 9: Commit**

```bash
./mvnw spotless:apply
git add ledger-service/src
git commit -m "feat(ledger-service): consume loan.approved into ledger entries"
```

---

## PR 3: infrastructure

### Task 12: docker-compose — loan-db + loan-service

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add `postgres-loan`** after `postgres-ledger` (host port 5434):

```yaml
  postgres-loan:
    image: postgres:16
    environment:
      POSTGRES_DB: ledgerflow_loan
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5434:5432"
    volumes:
      - postgres-loan-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 10
```

- [ ] **Step 2: Add `loan-service`** after `ledger-service`:

```yaml
  loan-service:
    build:
      context: .
      dockerfile: loan-service/Dockerfile
    ports:
      - "8083:8083"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-loan:5432/ledgerflow_loan
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:19092
      # Dev-only value for the local compose stack, same rationale as payment-service's.
      JWT_SECRET: dev-only-insecure-jwt-signing-key-change-me-1234567890
    depends_on:
      postgres-loan:
        condition: service_healthy
      kafka:
        condition: service_healthy
```

- [ ] **Step 3: Add the volume** — in the bottom `volumes:` block:

```yaml
  postgres-loan-data:
```

- [ ] **Step 4: Validate compose syntax**

Run: `docker compose config --quiet` (if Docker is unavailable in the environment, a YAML parse check via `python -c "import yaml,sys; yaml.safe_load(open('docker-compose.yml'))"` or visual review is the fallback — AGENTS.md notes compose was never run end-to-end here)
Expected: no errors

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(infra): add loan-db and loan-service to docker compose"
```

---

### Task 13: k8s loan-db manifest

**Files:**
- Create: `k8s/12-postgres-loan.yaml`

- [ ] **Step 1: Create the manifest** — copy of `k8s/10-postgres-payment.yaml` with every
      `payment`-specific name swapped. Exact replacements, everything else byte-identical
      (Secret name, PVC name, Deployment/selector/labels, Service name, POSTGRES_DB):

| payment manifest | loan manifest |
|---|---|
| `postgres-payment-credentials` | `postgres-loan-credentials` |
| `postgres-payment-data` | `postgres-loan-data` |
| `postgres-payment` (Deployment + selector + Service) | `postgres-loan` |
| `ledgerflow_payment` | `ledgerflow_loan` |

- [ ] **Step 2: Validate YAML**

Run: `python -c "import yaml; yaml.safe_load(open('k8s/12-postgres-loan.yaml', encoding='utf-8')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Commit**

```bash
git add k8s/12-postgres-loan.yaml
git commit -m "feat(infra): add loan-db k8s manifest"
```

---

### Task 14: CI — provision `ledgerflow_loan`

**Files:**
- Modify: `.github/workflows/maven.yml`

- [ ] **Step 1: Add a database-creation step** after the existing `Create ledger-service database` step:

```yaml
    - name: Create loan-service database
      run: PGPASSWORD=password psql -h localhost -U postgres -c "CREATE DATABASE ledgerflow_loan;"
```

Without this, CI's database provisioning is inconsistent across the three services; the
loan smoke test's datasource follows payment/ledger's test-classpath pattern, but CI should
provision `ledgerflow_loan` the same way it provisions the other two so the setup holds
regardless of which classpath a future smoke test ends up using.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/maven.yml
git commit -m "ci: create ledgerflow_loan database in maven workflow"
```

---

### Task 15: Docs sync

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: README.md** — update the "two microservices" framing:
  - Key features bullet: "Two decoupled microservices" → "Three decoupled microservices
    (`payment-service`, `ledger-service`, `loan-service`)".
  - Architecture tree: add `loan-service/` line (`owns loans; second Kafka producer; JWT
    resource server`) and note ledger-service also consumes `loan.approved`.
  - Architecture section: add loan-service (:8083) to the compose stack description.
- [ ] **Step 2: AGENTS.md** — update:
  - Repo structure block: add `loan-service/` line.
  - "Module boundaries": loan-service owns `loans`; is a second Kafka producer; JWT-protected.
  - "Kafka event contract": add a paragraph — `loan.approved` topic, `LoanApprovedEvent`
    (eventId/loanId/userId/amount/currency), keyed by loanId, same no-type-headers decision;
    ledger-service's loan listener uses its own consumer factory because the yml default type
    is pinned to `PaymentCompletedEvent`.
  - "Authentication" section: loan-service now also runs the shared-HMAC resource server
    pattern.
- [ ] **Step 3: Commit**

```bash
git add README.md AGENTS.md
git commit -m "docs: add loan-service to architecture docs"
```

---

### Task 16: Final verification

- [ ] **Step 1:** `./mvnw clean spotless:apply install -B` — expect `BUILD SUCCESS` (this is exactly what CI runs; Spotless check is bound to `verify`).
- [ ] **Step 2:** `git log --oneline` — confirm the commit series matches the tasks above.
- [ ] **Step 3:** If Docker is available: `docker compose up --build -d` then login against `:8083` and approve a loan; verify `loan.approved` in Kafdrop and a `ledger_entries` row with `loan_id` set. If Docker is unavailable (historically true in this environment), state that this step was not run and what remains unverified.
