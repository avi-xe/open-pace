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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import org.openpace.actor.Actor;

/**
 * Service for activity business logic.
 *
 * Handles creation, retrieval, and processing of activities with support for
 * both Note objects (TEXT storage) and custom types (JSONB storage).
 */
@ApplicationScoped
public class ActivityService {

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GpxService gpxService;

    /**
     * Create a new activity from an ActivityPub JSON payload.
     *
     * @param actor the actor creating the activity
     * @param activityJson the ActivityPub activity JSON
     * @return the persisted activity entity
     */
    public Activity createActivity(Actor actor, JsonNode activityJson) {
        String type = activityJson.has("type") ? activityJson.get("type").asText() : "Create";
        String baseUrl = actor.getActorId("").replace("/users/" + actor.username, "");
        String activityId = actor.getActorId(baseUrl) + "/activities/" + System.currentTimeMillis();

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = type;
        activity.activityId = activityId;
        activity.publishedAt = LocalDateTime.now();
        activity.createdAt = LocalDateTime.now();

        JsonNode object = activityJson.get("object");
        if (object != null) {
            if (object.isTextual()) {
                activity.objectId = object.asText();
            } else if (object.isObject()) {
                activity.objectId = object.has("id") ? object.get("id").asText() : null;
                activity.objectType = object.has("type") ? object.get("type").asText() : "Note";

                if ("Note".equals(activity.objectType)) {
                    activity.objectContent = object.has("content") ? object.get("content").asText() : null;
                } else {
                    // Store custom types as JSONB
                    activity.objectJson = object;

                    // Handle GPX data if present
                    JsonNode gpxNode = object.get("gpxData");
                    if (gpxNode != null && gpxNode.isTextual()) {
                        String gpxXml = gpxNode.asText();
                        GpxData gpxData = gpxService.parseGpx(gpxXml);

                        if (gpxData != null) {
                            activity.gpxData = gpxXml;
                            activity.trackData = convertGpxDataToJsonNode(gpxData);

                            // Auto-populate distance and duration from GPX summary
                            if (activity.objectJson != null && activity.objectJson.isObject()) {
                                ObjectNode objNode = (ObjectNode) activity.objectJson;
                                objNode.put("distance", gpxData.summary.totalDistance);
                                objNode.put("duration", gpxData.summary.totalDuration);
                                objNode.put("averagePace", gpxData.summary.averagePace);
                                objNode.put("elevationGain", gpxData.summary.elevationGain);
                                objNode.put("elevationLoss", gpxData.summary.elevationLoss);
                            }
                        }
                    }
                }
            }
        }

        activityRepository.persist(activity);
        return activity;
    }

    /**
     * Get the object of an activity as a JsonNode.
     *
     * For Note objects, reconstructs from objectContent.
     * For custom types, returns the stored objectJson.
     *
     * @param activity the activity entity
     * @return the activity object as JsonNode
     */
    public JsonNode getActivityObject(Activity activity) {
        if (activity.objectJson != null) {
            return activity.objectJson;
        }

        // Reconstruct Note from objectContent
        ObjectNode noteNode = objectMapper.createObjectNode();
        noteNode.put("type", activity.objectType != null ? activity.objectType : "Note");
        noteNode.put("content", activity.objectContent);
        if (activity.objectId != null) {
            noteNode.put("id", activity.objectId);
        }
        return noteNode;
    }

    /**
     * Convert GpxData to a JsonNode for JSONB storage.
     */
    private JsonNode convertGpxDataToJsonNode(GpxData gpxData) {
        ObjectNode root = objectMapper.createObjectNode();

        // Store track points
        ArrayNode pointsArray = objectMapper.createArrayNode();
        for (TrackPoint point : gpxData.points) {
            ObjectNode pointNode = objectMapper.createObjectNode();
            pointNode.put("lat", point.latitude);
            pointNode.put("lon", point.longitude);
            pointNode.put("ele", point.elevation);
            if (point.timestamp != null) {
                pointNode.put("time", point.timestamp.toString());
            }
            pointNode.put("speed", point.speed);
            pointsArray.add(pointNode);
        }
        root.set("points", pointsArray);

        // Store summary
        ObjectNode summaryNode = objectMapper.createObjectNode();
        summaryNode.put("distance", gpxData.summary.totalDistance);
        summaryNode.put("duration", gpxData.summary.totalDuration);
        summaryNode.put("pace", gpxData.summary.averagePace);
        summaryNode.put("elevationGain", gpxData.summary.elevationGain);
        summaryNode.put("elevationLoss", gpxData.summary.elevationLoss);
        summaryNode.put("maxSpeed", gpxData.summary.maxSpeed);
        summaryNode.put("averageSpeed", gpxData.summary.averageSpeed);
        root.set("summary", summaryNode);

        return root;
    }
}
