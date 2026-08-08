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
package org.openpace.activity;

import org.openpace.actor.Actor;
import org.openpace.activity.models.ActivityPubModels;
import org.openpace.federation.FederationDeliveryService;
import org.openpace.social.Follower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Core ActivityPub service. Processes activities by type (Create, Follow, Like, Undo, Accept)
 * and builds ActivityPub JSON responses.
 *
 * @see <a href="https://www.w3.org/TR/activitypub/">ActivityPub Specification</a>
 */
@Singleton
public class ActivityPubService {

    private static final Logger LOG = Logger.getLogger(ActivityPubService.class.getName());

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FederationDeliveryService federationDeliveryService;

    @Context
    UriInfo uriInfo;

    @PostConstruct
    void init() {
        LOG.info("ActivityPubService initialized");
    }

    // ============================================================
    // Activity Processing (Inbox)
    // ============================================================

    /**
     * Process an incoming activity from the inbox.
     */
    @Transactional
    public void processActivity(JsonNode activityJson) {
        String type = activityJson.has("type") ? activityJson.get("type").asText() : null;
        if (type == null) {
            LOG.warning("Received activity without type, ignoring");
            return;
        }

        LOG.info("Processing activity of type: " + type);

        switch (type) {
            case "Create" -> processCreate(activityJson);
            case "Follow" -> processFollow(activityJson);
            case "Accept" -> processAccept(activityJson);
            case "Like" -> processLike(activityJson);
            case "Undo" -> processUndo(activityJson);
            default -> LOG.info("Unhandled activity type: " + type);
        }
    }

    private void processCreate(JsonNode activity) {
        LOG.info("Processing Create activity");
        // Store the activity for later retrieval via the activities endpoint
        // The actual object will be retrieved when the activity is requested
    }

    private void processFollow(JsonNode activity) {
        LOG.info("Processing Follow activity");
        // Accept the follow and store in followers table
        // Build and send Accept response
    }

    private void processAccept(JsonNode activity) {
        LOG.info("Processing Accept activity");
        // Mark the follow as accepted
    }

    private void processLike(JsonNode activity) {
        LOG.info("Processing Like activity");
        // Store the like
    }

    private void processUndo(JsonNode activity) {
        LOG.info("Processing Undo activity");
        JsonNode object = activity.get("object");
        if (object != null) {
            String objectType = object.has("type") ? object.get("type").asText() : null;
            if ("Follow".equals(objectType)) {
                LOG.info("Undo Follow - removing follower");
            } else if ("Like".equals(objectType)) {
                LOG.info("Undo Like - removing like");
            }
        }
    }

    // ============================================================
    // ActivityPub JSON Builders
    // ============================================================

    /**
     * Build the ActivityPub Person actor for a given user.
     *
     * Per ActivityPub spec, inbox, outbox, followers, and following are all string URLs.
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
    public ActivityPubModels.OrderedCollectionPage buildOutboxPage(Actor actor, java.util.List<ActivityPubModels.Activity> activities) {
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
    public ActivityPubModels.OrderedCollectionPage buildFollowersPage(Actor actor, java.util.List<String> followerUrls) {
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
     * Build an ActivityPub Activity from a database Activity entity.
     *
     * Handles both Note objects (from objectContent) and custom types (from objectJson).
     */
    public ActivityPubModels.Activity toActivity(Activity dbActivity) {
        String baseUrl = getBaseUrl();
        ActivityPubModels.Activity model = new ActivityPubModels.Activity();
        model.context = "https://www.w3.org/ns/activitystreams";
        model.type = dbActivity.activityType;
        model.id = dbActivity.activityId;
        model.actor = dbActivity.actor.getActorId(baseUrl);
        model.published = dbActivity.publishedAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // Build object based on type and storage
        if ("Create".equals(dbActivity.activityType)) {
            // Check if we have stored JSON (custom type like Run, Ride, etc.)
            if (dbActivity.objectJson != null) {
                // Use the stored JSON directly for custom types
                model.object = dbActivity.objectJson;
            } else {
                // Reconstruct Note from objectContent
                ActivityPubModels.Note note = new ActivityPubModels.Note();
                note.type = dbActivity.objectType != null ? dbActivity.objectType : "Note";
                note.id = dbActivity.objectId;
                note.content = dbActivity.objectContent;
                note.attributedTo = model.actor;
                note.published = model.published;
                model.object = note;
            }
        } else {
            // For non-Create activities (Follow, Like, etc.), object is just the URL
            model.object = dbActivity.objectId;
        }

        return model;
    }

    // ============================================================
    // Helpers
    // ============================================================

    public String getBaseUrl() {
        return uriInfo.getBaseUri().getScheme() + "://" + uriInfo.getBaseUri().getHost()
            + (uriInfo.getBaseUri().getPort() != -1 ? ":" + uriInfo.getBaseUri().getPort() : "");
    }
}
