package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.time.Instant;
import java.util.Date;

public final class LegacyTokenUtil {
    private static final String SECRET = System.getenv().getOrDefault("AUTH_TOKEN_SECRET", "workshop-local-dev-secret");
    private static final String ISSUER = System.getenv().getOrDefault("AUTH_TOKEN_ISSUER", "workshop-auth-backend");
    private static final long TTL_SECONDS = Long.parseLong(System.getenv().getOrDefault("AUTH_TOKEN_TTL_SECONDS", "3600"));

    private LegacyTokenUtil() {}

    public static String create(String username, String role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(TTL_SECONDS);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .sign(Algorithm.HMAC256(SECRET));
    }
}
