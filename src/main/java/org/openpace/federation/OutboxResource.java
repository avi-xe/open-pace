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
package org.openpace.federation;

import org.openpace.actor.Actor;
import org.openpace.activity.Activity;
import org.openpace.activity.ActivityPubService;
import org.openpace.activity.ActivityService;
import org.openpace.activity.models.ActivityPubModels;
import org.openpace.shared.ErrorResponse;
import org.openpace.social.Follower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ActivityPub outbox endpoint.
 *
 * Handles C2S (client-to-server) activity submission and returns the actor's
 * outbox as an OrderedCollection.
 */
@Path("/users/{username}/outbox")
public class OutboxResource {

    private static final Logger LOG = Logger.getLogger(OutboxResource.class.getName());

    @Inject
    ActivityPubService activityPubService;

    @Inject
    ActivityService activityService;

    @Inject
    FederationDeliveryService federationDeliveryService;

    @Inject
    ObjectMapper objectMapper;

    @Context
    UriInfo uriInfo;

    /**
     * POST to outbox — submit an activity (C2S pattern).
     *
     * @see <a href="https://www.w3.org/TR/activitypub/#client-to-server">C2S Specification</a>
     */
    @POST
    @Consumes(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    @Transactional
    public Response postOutbox(
            @PathParam("username") String username,
            String body) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        LOG.info("Received outbox activity from: " + username);

        try {
            JsonNode activityJson = objectMapper.readTree(body);

            // Validate required fields
            String type = activityJson.has("type") ? activityJson.get("type").asText() : null;
            if (type == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("MISSING_ACTIVITY_TYPE", "Activity must have a 'type' field"))
                    .build();
            }

            // Use ActivityService to create activity (handles JSONB storage for custom types)
            Activity dbActivity = activityService.createActivity(actor, activityJson);

            LOG.info("Created activity: " + dbActivity.activityId);

            // For Create activities, deliver to followers (only if public)
            if ("Create".equals(type) && "public".equals(dbActivity.visibility)) {
                ActivityPubModels.Activity activityModel = activityPubService.toActivity(dbActivity);
                String activityJsonStr = objectMapper.writeValueAsString(activityModel);

                List<Follower> followers = Follower.findByActor(actor);
                for (Follower follower : followers) {
                    federationDeliveryService.deliver(follower.followerInbox, activityJsonStr);
                }
            }

            // Return 201 Created with Location header
            URI activityUri = URI.create(dbActivity.activityId);
            return Response.created(activityUri)
                .entity(Map.of("id", dbActivity.activityId))
                .build();

        } catch (Exception e) {
            LOG.warning("Failed to process outbox activity: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_ACTIVITY", e.getMessage()))
                .build();
        }
    }

    /**
     * GET outbox — returns the outbox as an OrderedCollection with items.
     */
    @GET
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getOutbox(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        // Get activities for this actor (exclude private)
        List<Activity> activities = org.openpace.activity.Activity.find("actor = ?1 AND visibility != 'private' ORDER BY publishedAt DESC", actor).list();
        List<ActivityPubModels.Activity> activityModels = activities.stream()
            .map(a -> activityPubService.toActivity(a))
            .toList();

        // Build collection with items embedded
        ActivityPubModels.OrderedCollection outbox = activityPubService.buildOutbox(actor);
        outbox.orderedItems = activityModels.stream().map(a -> a.id).toList();

        return Response.ok(outbox).build();
    }

    /**
     * GET outbox page — returns a page of the outbox collection.
     */
    @GET
    @Path("/page")
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getOutboxPage(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        // Get activities for this actor (exclude private)
        List<Activity> activities = org.openpace.activity.Activity.find("actor = ?1 AND visibility != 'private' ORDER BY publishedAt DESC", actor).list();
        List<ActivityPubModels.Activity> activityModels = activities.stream()
            .map(a -> activityPubService.toActivity(a))
            .toList();

        ActivityPubModels.OrderedCollectionPage page = activityPubService.buildOutboxPage(actor, activityModels);
        return Response.ok(page).build();
    }

    private String getBaseUrl() {
        return uriInfo.getBaseUri().getScheme() + "://" + uriInfo.getBaseUri().getHost()
            + (uriInfo.getBaseUri().getPort() != -1 ? ":" + uriInfo.getBaseUri().getPort() : "");
    }
}
