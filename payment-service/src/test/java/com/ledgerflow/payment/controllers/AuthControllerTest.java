package com.ledgerflow.payment.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.payment.dtos.LoginRequest;
import com.ledgerflow.payment.dtos.LoginResponse;
import com.ledgerflow.payment.dtos.PaymentRequest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Verifies the actual guarantee this milestone adds: an anonymous request to the payments API is
 * rejected, a demo login issues a usable JWT, and that JWT is accepted on the payments API.
 * {@code @EmbeddedKafka} is required here purely because {@code spring.kafka.bootstrap-servers} in
 * src/test/resources/application.yml resolves to {@code ${spring.embedded.kafka.brokers}} for every
 * test in this module - it isn't otherwise exercised by these assertions.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@EmbeddedKafka(partitions = 1, topics = "payment.completed")
class AuthControllerTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void unauthenticatedPaymentRequestIsRejected() {
    PaymentRequest request = samplePaymentRequest();

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/payments", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void loginWithValidCredentialsReturnsAToken() {
    ResponseEntity<LoginResponse> response = login();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getToken()).isNotBlank();
  }

  @Test
  void loginWithWrongPasswordIsRejected() {
    LoginRequest request =
        LoginRequest.builder().username("demo").password("not-the-password").build();

    ResponseEntity<LoginResponse> response =
        restTemplate.postForEntity("/api/v1/auth/login", request, LoginResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void authenticatedPaymentRequestSucceeds() {
    String token = login().getBody().getToken();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v1/payments", new HttpEntity<>(samplePaymentRequest(), headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private ResponseEntity<LoginResponse> login() {
    LoginRequest request =
        LoginRequest.builder().username("demo").password("demo-password").build();
    return restTemplate.postForEntity("/api/v1/auth/login", request, LoginResponse.class);
  }

  private PaymentRequest samplePaymentRequest() {
    return PaymentRequest.builder()
        .userId(1L)
        .amount(new BigDecimal("10.00"))
        .currency("USD")
        .idempotencyKey(UUID.randomUUID().toString())
        .build();
  }
}
