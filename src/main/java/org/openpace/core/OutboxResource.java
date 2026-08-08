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
package org.openpace.core;

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
import java.time.LocalDateTime;
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
    @Transactional
    public Response postOutbox(
            @PathParam("username") String username,
            String body) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Received outbox activity from: " + username);

        try {
            JsonNode activityJson = objectMapper.readTree(body);

            // Validate required fields
            String type = activityJson.has("type") ? activityJson.get("type").asText() : null;
            if (type == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Bad Request", "Activity must have a 'type' field"))
                    .build();
            }

            // Generate activity ID
            String activityId = actor.getActorId(getBaseUrl()) + "/activities/" + System.currentTimeMillis();

            // Create and persist the activity
            org.openpace.core.Activity dbActivity = new org.openpace.core.Activity();
            dbActivity.actor = actor;
            dbActivity.activityType = type;
            dbActivity.activityId = activityId;
            dbActivity.publishedAt = LocalDateTime.now();
            dbActivity.createdAt = LocalDateTime.now();

            // Extract object information
            JsonNode object = activityJson.get("object");
            if (object != null) {
                if (object.isTextual()) {
                    dbActivity.objectId = object.asText();
                } else if (object.isObject()) {
                    dbActivity.objectId = object.has("id") ? object.get("id").asText() : null;
                    dbActivity.objectType = object.has("type") ? object.get("type").asText() : "Note";

                    if ("Note".equals(dbActivity.objectType) || "Create".equals(type)) {
                        dbActivity.objectContent = object.has("content") ? object.get("content").asText() : null;
                    }
                }
            }

            dbActivity.persist();

            LOG.info("Created activity: " + activityId);

            // For Create activities, deliver to followers
            if ("Create".equals(type)) {
                ActivityPubModels.Activity activityModel = activityPubService.toActivity(dbActivity);
                String activityJsonStr = objectMapper.writeValueAsString(activityModel);

                List<Follower> followers = Follower.findByActor(actor);
                for (Follower follower : followers) {
                    federationDeliveryService.deliver(follower.followerInbox, activityJsonStr);
                }
            }

            return Response.accepted().entity(Map.of("id", activityId)).build();

        } catch (Exception e) {
            LOG.warning("Failed to process outbox activity: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Bad Request", e.getMessage()))
                .build();
        }
    }

    /**
     * GET outbox — returns the outbox as an OrderedCollection.
     */
    @GET
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getOutbox(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ActivityPubModels.OrderedCollection outbox = activityPubService.buildOutbox(actor);
        return Response.ok(outbox).build();
    }

    private String getBaseUrl() {
        return uriInfo.getBaseUri().getScheme() + "://" + uriInfo.getBaseUri().getHost()
            + (uriInfo.getBaseUri().getPort() != -1 ? ":" + uriInfo.getBaseUri().getPort() : "");
    }
}
