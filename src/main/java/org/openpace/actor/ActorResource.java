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
package org.openpace.actor;

import org.openpace.activity.ActivityPubService;
import org.openpace.activity.models.ActivityPubModels;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

/**
 * ActivityPub actor endpoint.
 *
 * Returns the Actor object for a user, serialized as application/activity+json.
 */
@Path("/users/{username}")
public class ActorResource {

    private static final Logger LOG = Logger.getLogger(ActorResource.class.getName());

    @Inject
    ActivityPubService activityPubService;

    @GET
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getActor(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Returning actor profile for: " + username);
        ActivityPubModels.Actor model = activityPubService.buildActor(actor);
        return Response.ok(model).build();
    }
}
