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

RUN chmod +x mvnw && ./mvnw -q -pl payment-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
