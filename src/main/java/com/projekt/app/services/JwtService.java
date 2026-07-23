package com.projekt.app.services;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${app.jwt.expiration-ms}")
  private long expirationMs;

  private final JwtEncoder jwtEncoder;

  public JwtService(JwtEncoder jwtEncoder) {
    this.jwtEncoder = jwtEncoder;
  }

  public String generateToken(String username) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder().subject(username).issuedAt(now)
        .expiresAt(now.plusMillis(expirationMs)).build();

    JwsHeader header = JwsHeader.with(() -> "HS256").build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

}
