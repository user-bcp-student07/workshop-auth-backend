package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@ApplicationScoped
public class JwtTokenService implements TokenService {
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long ttlSeconds;
    private final Clock clock;

    @Inject
    public JwtTokenService(
            @ConfigProperty(name = "auth.token.secret", defaultValue = "WORKSHOP_SECRET_2026") String secret,
            @ConfigProperty(name = "auth.token.ttl-seconds", defaultValue = "3600") long ttlSeconds
    ) {
        this(secret, ttlSeconds, Clock.systemUTC());
    }

    JwtTokenService(String secret, long ttlSeconds, Clock clock) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(this.algorithm).build();
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    @Override
    public IssuedToken issueToken(String username, String client) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plusSeconds(ttlSeconds);

        String token = JWT.create()
                .withSubject(username)
                .withClaim("client", client)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);

        return new IssuedToken(token, Math.max(0, expiry.getEpochSecond() - now.getEpochSecond()));
    }

    @Override
    public TokenClaims validateToken(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            String subject = jwt.getSubject();
            String client = jwt.getClaim("client").asString();
            Date issuedAt = jwt.getIssuedAt();
            Date expiresAt = jwt.getExpiresAt();

            if (subject == null || subject.isBlank() || client == null || client.isBlank() || issuedAt == null || expiresAt == null) {
                throw new IllegalArgumentException("Token is missing required claims");
            }

            return new TokenClaims(subject, client, issuedAt.toInstant().getEpochSecond(), expiresAt.toInstant().getEpochSecond());
        } catch (JWTVerificationException error) {
            throw new IllegalArgumentException("Token is invalid or expired", error);
        }
    }
}
