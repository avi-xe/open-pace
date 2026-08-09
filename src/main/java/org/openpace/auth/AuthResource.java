/*
 * Copyright 2024 Open Pace Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openpace.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.logging.Logger;
import org.openpace.actor.Actor;
import org.openpace.shared.ErrorResponse;
import org.openpace.shared.RsaKeyUtils;

/**
 * REST endpoints for user authentication.
 */
@Path("/api/auth")
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class.getName());

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Register a new user and create a linked Actor.
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response register(JsonNode body) {
        String username = body.has("username") ? body.get("username").asText().trim() : "";
        String password = body.has("password") ? body.get("password").asText() : "";
        String email = body.has("email") && !body.get("email").isNull()
                ? body.get("email").asText().trim() : null;

        // Validate username
        if (username.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "Username must not be blank"))
                    .build();
        }

        // Validate password length
        if (password.length() < 8) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("VALIDATION_ERROR", "Password must be at least 8 characters"))
                    .build();
        }

        // Check username uniqueness in User table
        if (User.findByUsername(username) != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("DUPLICATE_USERNAME", "Username '" + username + "' is already taken"))
                    .build();
        }

        // Check username uniqueness in Actor table
        if (Actor.findByUsername(username) != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("DUPLICATE_USERNAME", "Username '" + username + "' is already taken"))
                    .build();
        }

        // Create user with hashed password
        User user = User.create(username, password, "user");
        user.email = email;
        user.persist();
        LOG.info("Created user: " + user.username);

        // Create linked actor with RSA key pair for federation
        Actor actor = new Actor(username, username);
        actor.userId = user.id;
        java.security.KeyPair keyPair = RsaKeyUtils.generateKeyPair();
        actor.publicKey = RsaKeyUtils.publicKeyToPem(keyPair.getPublic());
        actor.privateKey = RsaKeyUtils.privateKeyToPem(keyPair.getPrivate());
        actor.persist();
        LOG.info("Created actor for user: " + username);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", user.id, "username", user.username))
                .build();
    }

    /**
     * Get current authenticated user info.
     */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @jakarta.annotation.security.RolesAllowed("user")
    public Response me() {
        String username = securityIdentity.getPrincipal().getName();

        User user = User.findByUsername(username);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("USER_NOT_FOUND", "User '" + username + "' not found"))
                    .build();
        }

        return Response.ok(Map.of(
                "id", user.id,
                "username", user.username,
                "email", user.email != null ? user.email : "",
                "role", user.role
        )).build();
    }
}
