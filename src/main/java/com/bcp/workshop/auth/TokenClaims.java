package com.bcp.workshop.auth;

public record TokenClaims(String subject, String client, long issuedAtEpochSecond, long expiresAtEpochSecond) {
}
