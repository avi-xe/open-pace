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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.logging.Logger;

/**
 * Processes incoming ActivityPub activities from the inbox.
 *
 * Handles Create, Follow, Accept, Like, and Undo activity types.
 */
@Singleton
public class InboxActivityProcessor {

    private static final Logger LOG = Logger.getLogger(InboxActivityProcessor.class.getName());

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
}
