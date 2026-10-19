package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LegacyUserRepository implements UserRepository {
    @Inject
    LegacyDatabase database;

    @Override
    public UserRecord findByCredentials(String username, String password) {
        return database.findByCredentials(username, password);
    }
}
