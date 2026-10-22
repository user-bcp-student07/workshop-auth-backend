package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {
    @Test
    void validatesIssuedTokenWithRequiredClaims() {
        JwtTokenService tokenService = new JwtTokenService("WORKSHOP_SECRET_2026", 3600, Clock.systemUTC());

        IssuedToken issuedToken = tokenService.issueToken("student", "angular-workshop");
        TokenClaims claims = tokenService.validateToken(issuedToken.value());

        assertEquals("student", claims.subject());
        assertEquals("angular-workshop", claims.client());
        assertEquals(3600, issuedToken.expiresInSeconds());
    }

    @Test
    void rejectsExpiredTokenDuringValidation() {
        Clock pastClock = Clock.fixed(Instant.now().minusSeconds(120), ZoneOffset.UTC);
        JwtTokenService tokenService = new JwtTokenService("WORKSHOP_SECRET_2026", 10, pastClock);

        IssuedToken issuedToken = tokenService.issueToken("student", "angular-workshop");

        assertThrows(IllegalArgumentException.class, () -> tokenService.validateToken(issuedToken.value()));
    }
}
