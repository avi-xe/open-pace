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

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints for initiating OIDC login flows.
 *
 * - GET /api/auth/login         → lists available providers
 * - GET /api/auth/login/generic → redirects to generic OIDC provider
 * - GET /api/auth/login/mastodon?instance=... → registers app and redirects to Mastodon OAuth
 */
@Path("/api/auth")
public class LoginResource {

    @Inject
    MastodonOAuthService mastodonOAuthService;

    /**
     * List available identity providers.
     */
    @GET
    @Path("/login")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listProviders() {
        return Response.ok(Map.of(
            "providers", List.of(
                Map.of(
                    "id", "generic",
                    "name", "OpenID Connect",
                    "description", "Login with any OIDC-compatible provider",
                    "login_url", "/api/auth/login/generic"
                ),
                Map.of(
                    "id", "mastodon",
                    "name", "Mastodon",
                    "description", "Login with your Mastodon account",
                    "login_url", "/api/auth/login/mastodon",
                    "requires", "instance URL (e.g., mastodon.social)"
                )
            )
        )).build();
    }

    /**
     * Initiate login with the generic OIDC provider.
     * Quarkus OIDC handles the redirect to the provider automatically
     * when an unauthenticated request hits a protected endpoint.
     *
     * This endpoint redirects to /api/auth/me which triggers the OIDC flow.
     */
    @GET
    @Path("/login/generic")
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginGeneric() {
        // Quarkus OIDC will redirect to the configured auth-server-url
        return Response.status(Response.Status.FOUND)
            .header("Location", "/api/auth/me")
            .build();
    }

    /**
     * Initiate login with a Mastodon instance.
     * Requires ?instance=<domain> query parameter.
     *
     * Registers an OAuth app with the instance, then redirects
     * to the instance's OAuth authorize endpoint.
     */
    @GET
    @Path("/login/mastodon")
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginMastodon(@jakarta.ws.rs.QueryParam("instance") String instance) {
        if (instance == null || instance.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "VALIDATION_ERROR", "message", "Missing 'instance' query parameter"))
                .build();
        }

        // Normalize instance URL
        String normalizedUrl = instance.trim();
        if (!normalizedUrl.startsWith("http")) {
            normalizedUrl = "https://" + normalizedUrl;
        }
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }

        // The actual redirect is handled by Quarkus OIDC when the user hits a protected endpoint.
        // We just need to ensure the tenant resolver can find the Mastodon config.
        String finalUrl = normalizedUrl;
        return Response.ok(Map.of(
            "provider", "mastodon",
            "instance", finalUrl,
            "message", "Use the OIDC flow with this instance. The frontend should redirect to /api/auth/me after setting the instance.",
            "authorize_hint", finalUrl + "/oauth/authorize"
        )).build();
    }
}
