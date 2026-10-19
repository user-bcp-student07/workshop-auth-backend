package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void returnsSignedTokenWithJwtClaims() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");

        var decoded = JWT.decode(token);
        org.junit.jupiter.api.Assertions.assertEquals("student", decoded.getSubject());
        org.junit.jupiter.api.Assertions.assertEquals("STUDENT", decoded.getClaim("role").asString());
        org.junit.jupiter.api.Assertions.assertNotNull(decoded.getExpiresAt());
    }

    @Test
    void logsInWhenOptionalClientIsOmitted() {
        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .statusCode(200)
                .body("user.username", is("student"));
    }

    @Test
    void rejectsNullRequest() {
        postLogin("null")
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        postLogin("{\"password\":\"Workshop123!\"}")
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsWhitespaceUsername() {
        postLogin("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        postLogin("{\"username\":\"student\"}")
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsWhitespacePassword() {
        postLogin("{\"username\":\"student\",\"password\":\"   \"}")
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentials() {
        postLogin("{\"username\":\"unknown-user\",\"password\":\"wrong\"}")
                .statusCode(401)
                .body("message", is("Invalid user unknown-user or password"));
    }

    @Test
    void rejectsCredentialsWithSingleQuoteAsLiteralInput() {
        postLogin("{\"username\":\"student' --\",\"password\":\"anything\"}")
                .statusCode(401)
                .body("message", is("Invalid user student' -- or password"));
    }

    @Test
    void storesHashedPasswordInsteadOfPlaintext() throws Exception {
        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .statusCode(200);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:./data/workshop.db");
             PreparedStatement statement = connection.prepareStatement("SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, "student");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                String storedPassword = result.getString("password");
                assertNotNull(storedPassword);
                assertFalse("Workshop123!".equals(storedPassword));
                assertTrue(storedPassword.startsWith("$2"));
            }
        }
    }

    @Test
    void returnsGenericShapeForUnexpectedException() {
        QuarkusMock.installMockForType(new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("controlled test failure");
            }
        }, LegacyDatabase.class);

        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .statusCode(500)
            .body("message", is("Unexpected error"))
            .body("exception", is("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void healthEndpointReportsUpWithNoRegisteredChecks() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void healthEndpointDoesNotRequireAuthorization() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void corsAllowsConfiguredOrigin() {
        given()
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", is("http://localhost:4200"));
    }

    @Test
    void corsDoesNotAllowUnlistedOrigin() {
        given()
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    private ValidatableResponse postLogin(String requestBody) {
        return given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/auth/login")
                .then();
    }
}
