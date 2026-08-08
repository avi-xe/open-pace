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
package org.openpace.analytics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.openpace.actor.Actor;
import org.openpace.shared.ErrorResponse;

/**
 * REST endpoint for user statistics.
 *
 * Returns aggregated statistics for a user's activity history.
 */
@Path("/users/{username}/stats")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    StatsService statsService;

    /**
     * GET stats — returns aggregated statistics for a user.
     *
     * @param username the username
     * @param period the time period filter: week, month, year, or all
     * @return JSON response with user statistics
     */
    @GET
    public Response getStats(@PathParam("username") String username,
            @QueryParam("period") @DefaultValue("all") String period) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTOR_NOT_FOUND", "User '" + username + "' not found"))
                .build();
        }

        StatsService.UserStats stats = statsService.getStats(actor.id, period);
        return Response.ok(stats).build();
    }
}
