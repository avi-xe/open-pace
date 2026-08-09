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

import org.openpace.activity.Activity;
import org.openpace.federation.protocol.ActivityPubModels;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Maps between domain entities and ActivityPub protocol models.
 *
 * This service handles the conversion between internal domain entities
 * (Activity, Actor) and external ActivityPub JSON representations.
 */
@Singleton
public class ActivityDomainMapper {

    @Inject
    ActivityPubModelBuilder modelBuilder;

    /**
     * Build an ActivityPub Activity from a database Activity entity.
     *
     * Handles both Note objects (from objectContent) and custom types (from objectJson).
     */
    public ActivityPubModels.Activity toActivity(Activity dbActivity) {
        String baseUrl = modelBuilder.getBaseUrl();
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
}
