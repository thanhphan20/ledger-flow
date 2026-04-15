# 💸 LedgerFlow

**LedgerFlow** is a modern, high-performance financial tracking system built with **Spring Boot 3 (4.0.5 Beta)** and **Java 21**. It provides a robust architecture for processing payments and maintaining an accurate, synchronized ledger of financial transactions.

[![Maven CI](https://github.com/thanhphan20/ledger-flow/actions/workflows/maven.yml/badge.svg)](https://github.com/thanhphan20/ledger-flow/actions/workflows/maven.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## 🚀 Key Features

- **Double-Module Architecture**: Logical separation between `Payment` (transaction initiation) and `Ledger` (accounting records).
- **Event-Driven Integration**: Uses Spring ApplicationEvents for decoupled communication between modules.
- **Automated Formatting**: Integrated with [Spotless](https://github.com/diffplug/spotless) and Google Java Format for pristine code quality.
- **Relational Integrity**: Backed by PostgreSQL with Spring Data JPA for data persistence.
- **Lombok Integration**: Minimal boilerplate code using modern Java annotations.

---

## 🛠️ Technology Stack

- **Core**: Java 21 (LTS)
- **Framework**: Spring Boot 4.0.5-SNAPSHOT
- **Database**: PostgreSQL
- **Persistence**: Spring Data JPA / Hibernate
- **Tooling**:
    - **Maven** (Build & Dependency Management)
    - **Spotless** (Code Formatting)
    - **GitHub Actions** (CI/CD)
    - **Lombok** (Productivity)

---

## 🏗️ Architecture Overview

The system follows a reactive and event-driven design pattern:

1. **Payment Initiation**: `PaymentService` processes a transaction and updates its status.
2. **Dynamic Ledgering**: Upon completion, the system creates a corresponding `LedgerEntry`.
3. **Event Propagation**: A `PaymentCompletedEvent` is published, allowing other modules (like notifications or audit logs) to react asynchronously via the `LedgerEventHandler`.

---

## 🏁 Getting Started

### Prerequisites

- **Java 21** or higher.
- **Maven 3.9+**
- **PostgreSQL** instance.

### Configuration

Update your `src/main/resources/application.properties` with your database credentials:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ledgerflow
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

### Running Locally

```bash
# Clone the repository
git clone https://github.com/thanhphan20/ledger-flow.git

# Build and Format
./mvnw spotless:apply clean install

# Run the application
./mvnw spring-boot:run
```

---

## 🤖 CI/CD & Code Quality

This project uses **GitHub Actions** for continuous integration. On every push to `main`:
1. The code is automatically formatted using **Spotless**.
2. A full build and test suite is executed.
3. If formatting changes are needed, the CI automatically commits the fixes.

To manually format code:
```bash
./mvnw spotless:apply
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Developed with ❤️ by [thanhphan20](https://github.com/thanhphan20)
