
package com.minicloud.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "mini-cloud";
    private static final long TOKEN_VALIDITY_MINUTES = 15;

    private final JwtEncoder jwtEncoder;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generateToken(
            Long userId,
            String username,
            String email,
            String role) {

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(username)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
