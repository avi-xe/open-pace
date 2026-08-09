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

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.openpace.shared.ErrorResponse;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * REST endpoints for user authentication.
 *
 * With OIDC, authentication is handled by the OIDC provider.
 * This resource provides:
 * - GET /api/auth/me → current user info from OIDC token
 */
@Path("/api/auth")
public class AuthResource {

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Get current authenticated user info from the OIDC token.
     * This endpoint triggers the OIDC Authorization Code flow
     * if the user is not authenticated.
     */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Transactional
    public Response me() {
        // Extract username from OIDC token or session
        String username = extractUsername();
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", "Not authenticated"))
                .build();
        }

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
                "display_name", user.displayName != null ? user.displayName : "",
                "role", user.role
        )).build();
    }

    /**
     * Extract the username from the security identity.
     * For OIDC, this comes from the token's preferred_username or sub claim.
     */
    private String extractUsername() {
        // Try JWT claims first
        if (securityIdentity.getPrincipal()instanceof JsonWebToken jwt) {
            // Prefer preferred_username (OIDC standard claim)
            String preferredUsername = jwt.getClaim("preferred_username");
            if (preferredUsername != null) {
                return preferredUsername;
            }
            // Fall back to sub (OIDC subject identifier)
            return jwt.getSubject();
        }
        // Fall back to principal name
        return securityIdentity.getPrincipal().getName();
    }
}
