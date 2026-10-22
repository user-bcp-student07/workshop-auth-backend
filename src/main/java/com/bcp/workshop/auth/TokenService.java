package com.bcp.workshop.auth;

public interface TokenService {
    IssuedToken issueToken(String username, String client);

    TokenClaims validateToken(String token);
}
