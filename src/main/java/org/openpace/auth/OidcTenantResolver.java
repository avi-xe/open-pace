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

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

/**
 * Resolves OIDC tenant configuration based on request path.
 *
 * Routes:
 * - /api/auth/login/generic → generic OIDC provider (configured in application.properties)
 * - /api/auth/login/mastodon/* → Mastodon OAuth2 (dynamically configured per instance)
 * - Other paths → default tenant (if configured)
 *
 * For Mastodon, the instance URL is stored in a session attribute by the LoginResource
 * before redirecting to the OIDC provider.
 */
@ApplicationScoped
public class OidcTenantResolver implements TenantConfigResolver {

    private static final String MASTODON_PROVIDER = "mastodon";

    @Inject
    MastodonOAuthService mastodonOAuthService;

    @Override
    public Uni<OidcTenantConfig> resolve(RoutingContext context, OidcRequestContext<OidcTenantConfig> requestContext) {
        String path = context.request().path();

        // Check if this is a Mastodon login callback or a Mastodon-protected path
        // The instance URL is stored in the session during the login initiation
        Object providerObj = context.session() != null
                ? context.session().get("oidc_provider")
                : null;
        String provider = providerObj instanceof String s ? s : null;

        if (MASTODON_PROVIDER.equals(provider)) {
            Object stored = context.session().get("mastodon_instance_url");
            String instanceUrl = stored instanceof String s ? s : null;
            if (instanceUrl != null) {
                return mastodonOAuthService.buildTenantConfig(instanceUrl, context);
            }
        }

        // Check path-based routing for login initiation
        if (path.startsWith("/api/auth/login/mastodon")) {
            // Extract instance URL from query parameter (queryParam returns List<String>)
            java.util.List<String> instanceParams = context.queryParam("instance");
            String instanceUrl = (instanceParams != null && !instanceParams.isEmpty()) ? instanceParams.get(0) : null;
            if (instanceUrl != null && !instanceUrl.isBlank()) {
                // Store in session for the callback
                if (context.session() != null) {
                    context.session().put("oidc_provider", MASTODON_PROVIDER);
                    context.session().put("mastodon_instance_url", instanceUrl);
                }
                return mastodonOAuthService.buildTenantConfig(instanceUrl, context);
            }
        }

        // For all other paths, use the default OIDC tenant (generic provider)
        // Return null to use the default tenant configuration from application.properties
        return Uni.createFrom().nullItem();
    }
}
