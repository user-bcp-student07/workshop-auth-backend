package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String DEFAULT_SECRET = "WORKSHOP_SECRET_2026";

    @Test
    void logsInWithWorkshopUserContractShape() {
    given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("$", hasKey("token"))
                .body("$", hasKey("expiresIn"))
                .body("$", hasKey("user"))
                .body("$", hasKey("debug"))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue());
    }

    @Test
    void rejectsInvalidCredentialsWith401() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"bad-password\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsNullRequestWith400() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsBlankUsernameWith400() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"  \",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankPasswordWith400() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"  \",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
        void clientValueDoesNotAlterSuccessfulAuthenticationInBaseline() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
            .body("user.username", is("student"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"mobile-app\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
            .body("user.username", is("student"));
        }

        @Test
        void exposesFixedExpiresInBaselineValue() {
        given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .body("expiresIn", is(3600));
    }

            @Test
            void loginTokenContainsRequiredClaimsAndExpiresInIsCoherent() {
            Response response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().response();

            String token = response.path("token");
            Number expiresIn = response.path("expiresIn");

            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(DEFAULT_SECRET)).build().verify(token);
            assertEquals("student", jwt.getSubject());
            assertEquals("angular-workshop", jwt.getClaim("client").asString());
            assertNotNull(jwt.getIssuedAt());
            assertNotNull(jwt.getExpiresAt());

            long nowEpochSeconds = System.currentTimeMillis() / 1000;
            long expEpochSeconds = jwt.getExpiresAt().toInstant().getEpochSecond();
            long expectedExpiresIn = expEpochSeconds - nowEpochSeconds;
            assertTrue(Math.abs(expectedExpiresIn - expiresIn.longValue()) <= 2);
            }

            @Test
            void rejectsInvalidClientWith400() {
            given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"  \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is required"));

            String longClient = "x".repeat(65);
            given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"" + longClient + "\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is invalid"));
            }

    @Test
    void doesNotLogSensitiveAuthenticationData() {
        TestLogHandler handler = new TestLogHandler();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.bcp.workshop.auth");
        logger.addHandler(handler);

        try {
            Response response = given()
                    .contentType("application/json")
                    .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .extract().response();

            String issuedToken = response.path("token");
            String logs = handler.joinedMessages();

            assertFalse(logs.contains("Workshop123!"));
            assertFalse(logs.contains("WORKSHOP_SECRET_2026"));
            assertFalse(logs.contains(issuedToken));
        } finally {
            logger.removeHandler(handler);
        }
    }

    static class TestLogHandler extends Handler {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        String joinedMessages() {
            return String.join("\n", messages);
        }
    }
}
