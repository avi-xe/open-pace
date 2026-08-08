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

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

/**
 * ActivityPub followers/following endpoints.
 */
@Path("/users/{username}")
public class FollowersResource {

    private static final Logger LOG = Logger.getLogger(FollowersResource.class.getName());

    @Inject
    ActivityPubService activityPubService;

    /**
     * GET followers — returns the followers as an OrderedCollection.
     */
    @GET
    @Path("/followers")
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getFollowers(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Returning followers for: " + username);
        ActivityPubModels.OrderedCollection followers = activityPubService.buildFollowers(actor);
        return Response.ok(followers).build();
    }

    /**
     * GET followers page — returns the followers as an OrderedCollectionPage.
     */
    @GET
    @Path("/followers/page")
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getFollowersPage(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<Follower> followerEntities = Follower.findByActor(actor);
        List<String> followerUrls = followerEntities.stream()
            .map(f -> f.followerActorUrl)
            .toList();

        ActivityPubModels.OrderedCollectionPage page = activityPubService.buildFollowersPage(actor, followerUrls);
        return Response.ok(page).build();
    }

    /**
     * GET following — returns the following as an OrderedCollection.
     */
    @GET
    @Path("/following")
    @Produces(ActivityPubModels.APPLICATION_ACTIVITY_JSON)
    public Response getFollowing(@PathParam("username") String username) {
        Actor actor = Actor.findByUsername(username);
        if (actor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.info("Returning following for: " + username);
        ActivityPubModels.OrderedCollection following = activityPubService.buildFollowing(actor);
        return Response.ok(following).build();
    }
}
