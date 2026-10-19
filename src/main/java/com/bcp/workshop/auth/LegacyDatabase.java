package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            }

            insertSeedUser("student", "Workshop123!", "Workshop Student", "STUDENT");
            insertSeedUser("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
            migratePlaintextPasswords();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private void insertSeedUser(String username, String password, String displayName, String role) {
        String sql = "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private void migratePlaintextPasswords() {
        String selectSql = "SELECT id, password FROM legacy_users";
        String updateSql = "UPDATE legacy_users SET password=? WHERE id=?";

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement selectStatement = connection.prepareStatement(selectSql);
             ResultSet result = selectStatement.executeQuery()) {
            while (result.next()) {
                String storedPassword = result.getString("password");
                if (storedPassword != null && !storedPassword.startsWith("$2")) {
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                        updateStatement.setString(1, BCrypt.hashpw(storedPassword, BCrypt.gensalt()));
                        updateStatement.setLong(2, result.getLong("id"));
                        updateStatement.executeUpdate();
                    }
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, password, display_name, role FROM legacy_users WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String storedPasswordHash = result.getString("password");
                    if (storedPasswordHash != null && BCrypt.checkpw(password, storedPasswordHash)) {
                        return new UserRecord(
                                result.getLong("id"),
                                result.getString("username"),
                                result.getString("display_name"),
                                result.getString("role")
                        );
                    }
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }
}
