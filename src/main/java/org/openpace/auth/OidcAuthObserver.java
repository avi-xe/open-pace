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

import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import io.quarkus.security.identity.SecurityIdentity;

import org.openpace.actor.Actor;

/**
 * Observes successful OIDC authentication events and auto-creates
 * local User + Actor + ExternalIdentity records on first login.
 *
 * This is the bridge between external OIDC identity and local accounts.
 */
public class OidcAuthObserver {

    private static final Logger LOG = Logger.getLogger(OidcAuthObserver.class.getName());

    /**
     * Called after a successful OIDC authentication.
     * Creates local user/actor/identity if they don't exist yet.
     */
    @Transactional
    public void onOidcAuthentication(@Observes SecurityIdentity identity) {
        if (identity.isAnonymous()) {
            return;
        }

        String username = extractUsername(identity);
        String email = extractEmail(identity);
        String displayName = extractDisplayName(identity);
        String provider = extractProvider(identity);
        String providerUserId = extractProviderUserId(identity);

        if (username == null || username.isBlank()) {
            LOG.warning("OIDC authentication succeeded but no username could be extracted");
            return;
        }

        // Find or create local User
        User user = User.findByUsername(username);
        if (user == null) {
            user = User.createFromOidc(username, email, displayName);
            user.persist();
            LOG.info("Created new user from OIDC: " + username);
        }

        // Create linked Actor if it doesn't exist
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            actor = new Actor(username, displayName != null ? displayName : username);
            actor.userId = user.id;
            actor.persist();
            LOG.info("Created new actor for user: " + username);
        }

        // Create ExternalIdentity if provider info is available
        if (provider != null && providerUserId != null) {
            ExternalIdentity existing = ExternalIdentity.findByProviderAndExternalId(provider, providerUserId);
            if (existing == null) {
                ExternalIdentity extIdentity = new ExternalIdentity();
                extIdentity.provider = provider;
                extIdentity.providerUserId = providerUserId;
                extIdentity.email = email;
                extIdentity.username = username;
                extIdentity.displayName = displayName;
                extIdentity.user = user;
                extIdentity.persist();
                LOG.info("Created external identity: " + provider + "/" + providerUserId + " → " + username);
            }
        }
    }

    private String extractUsername(SecurityIdentity identity) {
        if (identity.getPrincipal()instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            String preferredUsername = jwt.getClaim("preferred_username");
            if (preferredUsername != null) return preferredUsername;
            return jwt.getSubject();
        }
        return identity.getPrincipal().getName();
    }

    private String extractEmail(SecurityIdentity identity) {
        if (identity.getPrincipal()instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            return jwt.getClaim("email");
        }
        return null;
    }

    private String extractDisplayName(SecurityIdentity identity) {
        if (identity.getPrincipal()instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            String name = jwt.getClaim("name");
            if (name != null) return name;
            return jwt.getClaim("preferred_username");
        }
        return null;
    }

    private String extractProvider(SecurityIdentity identity) {
        if (identity.getPrincipal()instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            String issuer = jwt.getIssuer();
            if (issuer != null) {
                if (issuer.contains("mastodon")) return "mastodon";
                return "generic";
            }
        }
        return "generic";
    }

    private String extractProviderUserId(SecurityIdentity identity) {
        if (identity.getPrincipal()instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            return jwt.getSubject();
        }
        return null;
    }
}
