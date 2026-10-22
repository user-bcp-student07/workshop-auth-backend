package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.text.StringEscapeUtils;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final int MAX_CLIENT_LENGTH = 64;

    @Inject
    LegacyDatabase database;

    @Inject
    TokenService tokenService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            if (request == null) {
                LOG.warn("Rejected login request: request is null");
                return Response.status(400).entity(Map.of("message", "Request is null")).build();
            } else {
                if (request.username == null || request.username.trim().isEmpty()) {
                    LOG.warn("Rejected login request: username is required");
                    return Response.status(400).entity(Map.of("message", "Username is required")).build();
                } else {
                    if (request.password == null || request.password.trim().isEmpty()) {
                        LOG.warnf("Rejected login request for username %s: password is required", request.username);
                        return Response.status(400).entity(Map.of("message", "Password is required")).build();
                    } else {
                        String sanitizedClient = request.client == null ? null : request.client.trim();
                        if (sanitizedClient == null || sanitizedClient.isEmpty()) {
                            LOG.warnf("Rejected login request for username %s: client is required", request.username);
                            return Response.status(400).entity(Map.of("message", "Client is required")).build();
                        }

                        if (sanitizedClient.length() > MAX_CLIENT_LENGTH) {
                            LOG.warnf("Rejected login request for username %s: client is too long", request.username);
                            return Response.status(400).entity(Map.of("message", "Client is invalid")).build();
                        }

                        UserRecord user = database.findByCredentials(request.username, request.password);
                        if (user == null) {
                            LOG.warnf("Invalid credentials for username %s", request.username);
                            return Response.status(401).entity(Map.of("message", "Invalid user " + request.username + " or password")).build();
                        } else {
                            IssuedToken issuedToken = tokenService.issueToken(user.username(), sanitizedClient);
                            Map<String, Object> response = new HashMap<>();
                            response.put("token", issuedToken.value());
                            response.put("expiresIn", issuedToken.expiresInSeconds());
                            response.put("user", Map.of(
                                    "username", user.username(),
                                    "displayName", StringEscapeUtils.escapeHtml4(user.displayName()),
                                    "role", user.role()
                            ));
                            response.put("debug", "authenticated-with-legacy-flow");
                            LOG.infof("Login successful for username %s and client %s", user.username(), sanitizedClient);
                            return Response.ok(response).build();
                        }
                    }
                }
            }
        } catch (Exception error) {
            LOG.errorf(error, "Unexpected auth error in login flow for username %s", request == null ? "unknown" : request.username);
            return Response.status(500).entity(Map.of(
                    "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                    "exception", error.getClass().getName()
            )).build();
        }
    }
}
