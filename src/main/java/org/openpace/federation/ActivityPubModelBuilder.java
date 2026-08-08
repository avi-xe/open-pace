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
import org.openpace.activity.models.ActivityPubModels;
import org.openpace.social.Follower;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds ActivityPub JSON models from domain entities.
 *
 * This service is responsible for constructing AS2 collections, pages,
 * and activity objects from internal domain entities.
 */
@Singleton
public class ActivityPubModelBuilder {

    private static final Logger LOG = Logger.getLogger(ActivityPubModelBuilder.class.getName());

    @Context
    UriInfo uriInfo;

    /**
     * Build the ActivityPub Person actor for a given user.
     */
    public ActivityPubModels.Actor buildActor(Actor actor) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.Actor model = new ActivityPubModels.Actor();
        model.context = "https://www.w3.org/ns/activitystreams";
        model.type = ActivityPubModels.PERSON_TYPE;
        model.id = actor.getActorId(baseUrl);
        model.preferredUsername = actor.username;
        model.name = actor.name;
        model.inbox = actor.getInboxUrl(baseUrl);
        model.outbox = actor.getOutboxUrl(baseUrl);
        model.followers = actor.getFollowersUrl(baseUrl);
        model.following = actor.getFollowingUrl(baseUrl);
        return model;
    }

    /**
     * Build the ActivityPub OrderedCollection for an actor's outbox.
     */
    public ActivityPubModels.OrderedCollection buildOutbox(Actor actor) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.OrderedCollection outbox = new ActivityPubModels.OrderedCollection();
        outbox.context = "https://www.w3.org/ns/activitystreams";
        outbox.type = "OrderedCollection";
        outbox.id = actor.getOutboxUrl(baseUrl);
        outbox.totalItems = String.valueOf(Activity.count("actor", actor));
        outbox.first = actor.getOutboxUrl(baseUrl) + "/page";
        return outbox;
    }

    /**
     * Build the ActivityPub OrderedCollectionPage for an actor's outbox.
     */
    public ActivityPubModels.OrderedCollectionPage buildOutboxPage(Actor actor, List<ActivityPubModels.Activity> activities) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.OrderedCollectionPage page = new ActivityPubModels.OrderedCollectionPage();
        page.context = "https://www.w3.org/ns/activitystreams";
        page.type = "OrderedCollectionPage";
        page.id = actor.getOutboxUrl(baseUrl) + "/page";
        page.partOf = actor.getOutboxUrl(baseUrl);
        page.orderedItems = activities.stream()
            .map(a -> a.id)
            .toList();
        return page;
    }

    /**
     * Build the ActivityPub OrderedCollection for followers.
     */
    public ActivityPubModels.OrderedCollection buildFollowers(Actor actor) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.OrderedCollection followers = new ActivityPubModels.OrderedCollection();
        followers.context = "https://www.w3.org/ns/activitystreams";
        followers.type = "OrderedCollection";
        followers.id = actor.getFollowersUrl(baseUrl);
        followers.totalItems = String.valueOf(Follower.countByActor(actor));
        followers.first = actor.getFollowersUrl(baseUrl) + "/page";
        return followers;
    }

    /**
     * Build the ActivityPub OrderedCollectionPage for followers.
     */
    public ActivityPubModels.OrderedCollectionPage buildFollowersPage(Actor actor, List<String> followerUrls) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.OrderedCollectionPage page = new ActivityPubModels.OrderedCollectionPage();
        page.context = "https://www.w3.org/ns/activitystreams";
        page.type = "OrderedCollectionPage";
        page.id = actor.getFollowersUrl(baseUrl) + "/page";
        page.partOf = actor.getFollowersUrl(baseUrl);
        page.orderedItems = followerUrls;
        return page;
    }

    /**
     * Build the ActivityPub OrderedCollection for following.
     */
    public ActivityPubModels.OrderedCollection buildFollowing(Actor actor) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.OrderedCollection following = new ActivityPubModels.OrderedCollection();
        following.context = "https://www.w3.org/ns/activitystreams";
        following.type = "OrderedCollection";
        following.id = actor.getFollowingUrl(baseUrl);
        following.totalItems = "0";
        following.first = actor.getFollowingUrl(baseUrl) + "/page";
        return following;
    }

    /**
     * Get the base URL of the current instance.
     */
    public String getBaseUrl() {
        return uriInfo.getBaseUri().getScheme() + "://" + uriInfo.getBaseUri().getHost()
            + (uriInfo.getBaseUri().getPort() != -1 ? ":" + uriInfo.getBaseUri().getPort() : "");
    }
}
