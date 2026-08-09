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
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.oidc.OidcTenantConfig;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;

/**
 * Handles Mastodon OAuth2 integration.
 *
 * Since Mastodon instances are independent OAuth2 servers (not OIDC),
 * we dynamically register an OAuth app per instance and configure
 * Quarkus OIDC tenant accordingly.
 */
@ApplicationScoped
public class MastodonOAuthService {

    /** Cache registered apps per instance to avoid re-registration. */
    private final Map<String, MastodonApp> appCache = new ConcurrentHashMap<>();

    @Inject
    Vertx vertx;

    /**
     * Cached Mastodon OAuth app credentials for an instance.
     */
    public record MastodonApp(
        String instanceUrl,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl
    ) {}

    /**
     * Build an OIDC tenant configuration for a Mastodon instance.
     * Registers the app if not already cached.
     */
    public Uni<OidcTenantConfig> buildTenantConfig(String instanceUrl, RoutingContext context) {
        String normalizedUrl = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        String baseUrl = normalizedUrl.startsWith("http") ? normalizedUrl : "https://" + normalizedUrl;

        MastodonApp cached = appCache.get(baseUrl);
        if (cached != null) {
            return Uni.createFrom().item(buildConfig(cached));
        }

        return Uni.createFrom().emitter(emitter -> {
            registerApp(baseUrl)
                .onSuccess(app -> {
                    appCache.put(baseUrl, app);
                    emitter.complete(buildConfig(app));
                })
                .onFailure(emitter::fail);
        });
    }

    /**
     * Register an OAuth app with a Mastodon instance via POST /api/v1/apps.
     */
    private io.vertx.core.Future<MastodonApp> registerApp(String instanceUrl) {
        String redirectUri = buildRedirectUri(instanceUrl);
        WebClient client = WebClient.create(vertx);

        return client
            .postAbs(instanceUrl + "/api/v1/apps")
            .addQueryParam("client_name", "Open Pace")
            .addQueryParam("redirect_uris", redirectUri)
            .addQueryParam("scopes", "read write follow")
            .addQueryParam("website", "https://github.com/avi-xe/open-pace")
            .sendBuffer(Buffer.buffer(""))
            .map(response -> {
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to register Mastodon app: " + response.statusCode());
                }
                JsonObject body = response.bodyAsJsonObject();
                return new MastodonApp(
                    instanceUrl,
                    body.getString("client_id"),
                    body.getString("client_secret"),
                    instanceUrl + "/oauth/authorize",
                    instanceUrl + "/oauth/token"
                );
            });
    }

    /**
     * Build the OIDC tenant config from a Mastodon app.
     */
    private OidcTenantConfig buildConfig(MastodonApp app) {
        return OidcTenantConfig.builder()
            .tenantId("mastodon-" + app.instanceUrl().hashCode())
            .authServerUrl(app.authorizeUrl())
            .clientId(app.clientId())
            .credentials(app.clientSecret())
            .applicationType(io.quarkus.oidc.runtime.OidcTenantConfig.ApplicationType.HYBRID)
            .discoveryEnabled(false)
            .authorizationPath("/oauth/authorize")
            .tokenPath("/oauth/token")
            .userInfoPath("/api/v1/accounts/verify_credentials")
            .tenantPath("/api/auth/callback/mastodon")
            .build();
    }

    private String buildRedirectUri(String instanceUrl) {
        return "http://localhost:8080/api/auth/callback/mastodon";
    }

    /**
     * Fetch user info from a Mastodon instance after OAuth token is obtained.
     */
    public Uni<JsonObject> fetchUserInfo(String instanceUrl, String accessToken) {
        WebClient client = WebClient.create(vertx);

        return Uni.createFrom().emitter(emitter -> {
            client.getAbs(instanceUrl + "/api/v1/accounts/verify_credentials")
                .bearerTokenAuthentication(accessToken)
                .send()
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        emitter.fail(new RuntimeException("Failed to fetch Mastodon user info: " + response.statusCode()));
                    } else {
                        emitter.complete(response.bodyAsJsonObject());
                    }
                })
                .onFailure(emitter::fail);
        });
    }
}
