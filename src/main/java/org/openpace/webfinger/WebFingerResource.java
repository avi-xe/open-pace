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
package org.openpace.webfinger;

import org.openpace.actor.Actor;
import org.openpace.federation.ActivityPubModelBuilder;
import org.openpace.federation.protocol.ActivityPubModels;
import org.openpace.shared.ErrorResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * WebFinger discovery endpoint.
 *
 * @see <a href="https://www.w3.org/TR/activitypub/#actor-discovery">Actor Discovery via WebFinger</a>
 */
@Path("/.well-known")
public class WebFingerResource {

    private static final Logger LOG = Logger.getLogger(WebFingerResource.class.getName());

    @Inject
    ActivityPubModelBuilder modelBuilder;

    @GET
    @Path("/webfinger")
    @Produces("application/jrd+json")
    public Response webfinger(@QueryParam("resource") String resource) {
        if (resource == null || resource.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("MISSING_PARAMETER", "Missing 'resource' parameter"))
                .build();
        }

        LOG.info("WebFinger request for: " + resource);

        // Expected format: acct:username@domain
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

        // Look up the actor
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            LOG.info("Actor not found: " + username);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + username + "' not found"))
                .build();
        }

        String baseUrl = modelBuilder.getBaseUrl();
        String actorId = actor.getActorId(baseUrl);

        // Build WebFinger response
        Map<String, Object> response = Map.of(
            "subject", resource,
            "aliases", List.of(actorId),
            "links", List.of(
                Map.of(
                    "rel", "self",
                    "type", ActivityPubModels.APPLICATION_ACTIVITY_JSON,
                    "href", actorId
                )
            )
        );

        return Response.ok(response).build();
    }
}
