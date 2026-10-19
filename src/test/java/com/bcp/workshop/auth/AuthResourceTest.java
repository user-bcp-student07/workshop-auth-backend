package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
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
    void returnsGenericShapeForUnexpectedException() {
        QuarkusMock.installMockForType(new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("controlled test failure");
            }
        }, LegacyDatabase.class);

        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .statusCode(500)
                .body("message", is("controlled test failure"))
                .body("exception", is(IllegalStateException.class.getName()));
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

    private ValidatableResponse postLogin(String requestBody) {
        return given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/auth/login")
                .then();
    }
}
