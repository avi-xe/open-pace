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
import org.openpace.activity.ActivityPubService;
import org.openpace.activity.models.ActivityPubModels;
import org.openpace.shared.ErrorResponse;

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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

/**
 * ActivityPub inbox endpoint.
 *
 * Handles both S2S (server-to-server) and C2S (client-to-server) inbox processing.
 * For Sprint 1, this handles S2S delivery from remote servers.
 */
@Path("/users/{username}/inbox")
public class InboxResource {

    private static final Logger LOG = Logger.getLogger(InboxResource.class.getName());

    @Inject
    ActivityPubService activityPubService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * POST to inbox — receive an activity from a remote server or client.
     */
    @POST
    @Consumes(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    @Transactional
    public Response postInbox(
            @PathParam("username") String username,
            String body) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Received inbox activity for: " + username);

        try {
            JsonNode activityJson = objectMapper.readTree(body);
            activityPubService.processActivity(activityJson);
            return Response.accepted().build();
        } catch (Exception e) {
            LOG.warning("Failed to process inbox activity: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Bad Request", e.getMessage()))
                .build();
        }
    }

    /**
     * GET inbox — returns the inbox as an OrderedCollection.
     */
    @GET
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getInbox(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // For Sprint 1, return an empty inbox collection
        ActivityPubModels.OrderedCollection inbox = new ActivityPubModels.OrderedCollection();
        inbox.context = "https://www.w3.org/ns/activitystreams";
        inbox.type = "OrderedCollection";
        inbox.id = actor.getInboxUrl(activityPubService.getBaseUrl());
        inbox.totalItems = "0";
        inbox.first = null;

        return Response.ok(inbox).build();
    }
}
