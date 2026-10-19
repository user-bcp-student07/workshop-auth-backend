package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private static final long TOKEN_TTL_SECONDS = 3600;

    @Inject
    UserRepository userRepository;

    public AuthenticatedUser authenticate(String username, String password) {
        UserRecord user = userRepository.findByCredentials(username, password);
        if (user == null) {
            return null;
        }

        String token = LegacyTokenUtil.create(user.username(), user.role());
        return new AuthenticatedUser(user, token, TOKEN_TTL_SECONDS);
    }

    public record AuthenticatedUser(UserRecord user, String token, long expiresIn) {}
}
