package com.ledgerflow.payment.controllers;

import com.ledgerflow.payment.dtos.LoginRequest;
import com.ledgerflow.payment.dtos.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Endpoints for authentication")
public class AuthController {

  private static final long TOKEN_VALIDITY_SECONDS = 3600;

  private final AuthenticationManager authenticationManager;
  private final JwtEncoder jwtEncoder;

  @PostMapping("/login")
  @Operation(
      summary = "Log in",
      description = "Authenticates demo credentials and returns a signed JWT.")
  @ApiResponse(responseCode = "200", description = "Login successful")
  @ApiResponse(responseCode = "401", description = "Invalid credentials")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
    } catch (AuthenticationException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Instant now = Instant.now();
    Instant expiresAt = now.plus(TOKEN_VALIDITY_SECONDS, ChronoUnit.SECONDS);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("payment-service")
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(request.getUsername())
            .claim("roles", "ROLE_USER")
            .build();

    JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

    return ResponseEntity.ok(LoginResponse.builder().token(token).expiresAt(expiresAt).build());
  }
}
