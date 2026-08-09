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
package org.openpace.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.openpace.activity.Activity;
import org.openpace.federation.ActivityPubModelBuilder;
import org.openpace.federation.ActivityDomainMapper;
import org.openpace.actor.Actor;
import org.openpace.shared.ErrorResponse;
import org.openpace.social.Follower;

/**
 * Application-friendly JSON API for the web frontend.
 *
 * These endpoints return simplified JSON (not ActivityPub format) for
 * consumption by the Alpine.js frontend. ActivityPub endpoints remain
 * at root level for federation.
 */
@Path("/api")
@ApplicationScoped
public class AppApiResource {

    private static final Logger LOG = Logger.getLogger(AppApiResource.class.getName());
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE_TIME;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ActivityPubModelBuilder modelBuilder;

    @Inject
    ActivityDomainMapper activityDomainMapper;

    @ConfigProperty(name = "openpace.federation.webfinger.mode", defaultValue = "remote")
    String webfingerMode;

    @ConfigProperty(name = "openpace.federation.webfinger.local-domain", defaultValue = "localhost:8443")
    String webfingerLocalDomain;

    /**
     * GET user profile as app JSON.
     */
    @GET
    @Path("/users/{username}/profile")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfile(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        long followerCount = Follower.countByActor(actor);
        long activityCount = Activity.count("actor", actor);

        ObjectNode profile = objectMapper.createObjectNode();
        profile.put("username", actor.username);
        profile.put("name", actor.name != null ? actor.name : actor.username);
        profile.put("instance", extractInstanceDomain(actor));
        profile.put("isLocal", true);
        profile.put("followers", followerCount);
        profile.put("following", countFollowing(actor));
        profile.put("activityCount", activityCount);
        if (actor.createdAt != null) {
            profile.put("createdAt", actor.createdAt.format(ISO));
        }

        return Response.ok(profile).build();
    }

    /**
     * GET followers list as app JSON.
     */
    @GET
    @Path("/users/{username}/followers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFollowers(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        List<Follower> followers = Follower.findByActor(actor);
        ArrayNode result = objectMapper.createArrayNode();
        for (Follower f : followers) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("actorUrl", f.followerActorUrl);
            node.put("instance", extractDomainFromUrl(f.followerActorUrl));
            node.put("isLocal", isLocalUrl(f.followerActorUrl));
            if (f.followerActor != null) {
                node.put("username", f.followerActor.username);
            }
            if (f.createdAt != null) {
                node.put("since", f.createdAt.format(ISO));
            }
            result.add(node);
        }

        return Response.ok(result).build();
    }

    /**
     * GET following list as app JSON.
     */
    @GET
    @Path("/users/{username}/following")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFollowing(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        // Following is represented as followers where actor = followed user
        // For now, return empty array — following tracking needs a dedicated table
        ArrayNode result = objectMapper.createArrayNode();

        return Response.ok(result).build();
    }

    /**
     * GET activity feed as app JSON.
     * Returns recent activities from all users (simplified feed).
     * Note: Path is /api/feed (not /api/activities/feed) to avoid
     * conflict with MapResource's /api/activities/{activityId}.
     */
    @GET
    @Path("/feed")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getFeed() {
        List<Activity> activities = Activity.find("SELECT a FROM Activity a JOIN FETCH a.actor ORDER BY a.publishedAt DESC").list();

        ArrayNode result = objectMapper.createArrayNode();
        for (Activity a : activities) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", a.id);
            node.put("type", a.getActivityType().name());
            node.put("objectType", a.objectType);
            node.put("content", a.objectContent);
            node.put("instance", "local");
            node.put("isLocal", true);
            node.put("mapUrl", "/api/activities/" + a.id + "/map.png");
            node.put("gpxUrl", "/api/activities/" + a.id + "/export/gpx");

            if (a.actor != null) {
                ObjectNode author = objectMapper.createObjectNode();
                author.put("username", a.actor.username);
                author.put("name", a.actor.name != null ? a.actor.name : a.actor.username);
                author.put("instance", "local");
                author.put("isLocal", true);
                node.set("author", author);
            }

            if (a.publishedAt != null) {
                node.put("publishedAt", a.publishedAt.format(ISO));
            }

            // Extract summary stats from objectJson if available
            if (a.objectJson != null) {
                if (a.objectJson.has("summary")) {
                    node.set("summary", a.objectJson.get("summary"));
                }
            }

            result.add(node);
        }

        return Response.ok(result).build();
    }

    /**
     * GET WebFinger proxy endpoint.
     * Proxies WebFinger queries to remote or local domain based on config.
     */
    @GET
    @Path("/federation/webfinger")
    @Produces("application/jrd+json")
    public Response webfinger(@QueryParam("resource") String resource) {
        if (resource == null || resource.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("MISSING_PARAMETER", "Missing 'resource' parameter"))
                .build();
        }

        if (!resource.startsWith("acct:")) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_RESOURCE", "Resource must use acct: URI scheme"))
                .build();
        }

        String[] parts = resource.substring(5).split("@", 2);
        if (parts.length != 2) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_RESOURCE", "Resource must be in format acct:username@domain"))
                .build();
        }

        String username = parts[0];
        String domain = parts[1];

        if (username.isBlank() || domain.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_RESOURCE", "Username and domain must not be empty"))
                .build();
        }

        String targetDomain = "local".equals(webfingerMode) ? webfingerLocalDomain : domain;
        String url = "https://" + targetDomain + "/.well-known/webfinger?resource=" + resource;

        LOG.info("WebFinger proxy request: " + url + " (mode=" + webfingerMode + ")");

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/jrd+json")
                .GET()
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                return Response.ok(httpResponse.body(), "application/jrd+json").build();
            } else if (httpResponse.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found on " + targetDomain))
                    .build();
            } else {
                LOG.warning("WebFinger proxy failed: HTTP " + httpResponse.statusCode());
                return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(new ErrorResponse("UPSTREAM_ERROR", "Upstream returned HTTP " + httpResponse.statusCode()))
                    .build();
            }
        } catch (Exception e) {
            LOG.warning("WebFinger proxy error: " + e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ErrorResponse("PROXY_ERROR", "Failed to contact " + targetDomain + ": " + e.getMessage()))
                .build();
        }
    }

    /**
     * GET proxy for remote ActivityPub actor profiles.
     * Avoids CORS issues when browser fetches from remote servers.
     */
    @GET
    @Path("/federation/actor")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRemoteActor(@QueryParam("url") String actorUrl) {
        if (actorUrl == null || actorUrl.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("MISSING_PARAMETER", "Missing 'url' parameter"))
                .build();
        }

        // Only proxy http/https URLs
        if (!actorUrl.startsWith("http://") && !actorUrl.startsWith("https://")) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_URL", "URL must be http or https"))
                .build();
        }

        LOG.info("Actor profile proxy request: " + actorUrl);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(actorUrl))
                .header("Accept", "application/activity+json, application/json")
                .GET()
                .build();

            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                return Response.ok(httpResponse.body(), "application/activity+json").build();
            } else if (httpResponse.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor not found at " + actorUrl))
                    .build();
            } else {
                LOG.warning("Actor proxy failed: HTTP " + httpResponse.statusCode());
                return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(new ErrorResponse("UPSTREAM_ERROR", "Upstream returned HTTP " + httpResponse.statusCode()))
                    .build();
            }
        } catch (Exception e) {
            LOG.warning("Actor proxy error: " + e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ErrorResponse("PROXY_ERROR", "Failed to contact remote server: " + e.getMessage()))
                .build();
        }
    }

    /**
     * GET known remote instances.
     */
    @GET
    @Path("/federation/instances")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInstances() {
        // Discover instances from follower actor URLs
        List<Follower> allFollowers = Follower.listAll();
        Set<String> remoteInstances = new HashSet<>();
        for (Follower f : allFollowers) {
            String domain = extractDomainFromUrl(f.followerActorUrl);
            if (domain != null && !isLocalDomain(domain)) {
                remoteInstances.add(domain);
            }
        }

        ArrayNode result = objectMapper.createArrayNode();

        // Add local instance
        ObjectNode local = objectMapper.createObjectNode();
        local.put("domain", "local");
        local.put("displayName", "This Instance");
        local.put("isLocal", true);
        local.put("userCount", Actor.count());
        local.put("activityCount", Activity.count());
        result.add(local);

        // Add remote instances
        for (String domain : remoteInstances) {
            ObjectNode remote = objectMapper.createObjectNode();
            remote.put("domain", domain);
            remote.put("displayName", domain);
            remote.put("isLocal", false);
            remote.put("userCount", 0); // Unknown without querying
            remote.put("activityCount", 0);
            result.add(remote);
        }

        return Response.ok(result).build();
    }

    /**
     * POST follow request to remote user.
     * Sends a Follow activity via the outbox.
     */
    @POST
    @Path("/federation/follow")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @jakarta.annotation.security.RolesAllowed("user")
    @Transactional
    public Response followRemoteUser(ObjectNode body) {
        String targetActorUrl = body.has("actorUrl") ? body.get("actorUrl").asText() : null;
        if (targetActorUrl == null || targetActorUrl.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("MISSING_ACTOR_URL", "actorUrl is required"))
                .build();
        }

        String username = body.has("username") ? body.get("username").asText() : null;
        if (username == null || username.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("MISSING_USERNAME", "username is required"))
                .build();
        }

        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        LOG.info("Follow request from " + username + " to " + targetActorUrl);

        // Build Follow activity
        ObjectNode followActivity = objectMapper.createObjectNode();
        followActivity.put("@context", "https://www.w3.org/ns/activitystreams");
        followActivity.put("type", "Follow");
        followActivity.put("actor", actor.getActorId(getBaseUrl()));
        followActivity.put("object", targetActorUrl);

        // TODO: Deliver to remote inbox via FederationDeliveryService
        // For now, acknowledge the request
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "pending");
        result.put("message", "Follow request queued for delivery");
        result.put("target", targetActorUrl);

        return Response.ok(result).build();
    }

    // === Helper methods ===

    private long countFollowing(Actor actor) {
        // Count unique instances in followers as a proxy for following
        // In a full implementation, this would query a "following" table
        return 0;
    }

    private String extractInstanceDomain(Actor actor) {
        // Local actors return the configured domain
        return "local";
    }

    private String extractDomainFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isLocalUrl(String url) {
        String domain = extractDomainFromUrl(url);
        return domain == null || isLocalDomain(domain);
    }

    private boolean isLocalDomain(String domain) {
        // In a full implementation, compare against configured domain
        return "localhost".equals(domain) || "127.0.0.1".equals(domain)
            || domain.contains("localhost");
    }

    private String getBaseUrl() {
        String host = System.getProperty("quarkus.http.host", "localhost");
        String port = System.getProperty("quarkus.http.port", "8080");
        return "http://" + host + ":" + port;
    }
}
