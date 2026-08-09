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
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.openpace.actor.Actor;
import org.openpace.analytics.ComparisonService;
import org.openpace.analytics.PaceZoneService;
import org.openpace.analytics.PersonalRecordService;
import org.openpace.analytics.SplitService;

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

    @Inject
    PersonalRecordService personalRecordService;

    @Inject
    SplitService splitService;

    @Inject
    PaceZoneService paceZoneService;

    @Inject
    ComparisonService comparisonService;

    /**
     * Create a new activity from an ActivityPub JSON payload.
     *
     * @param actor the actor creating the activity
     * @param activityJson the ActivityPub activity JSON
     * @return the persisted activity entity
     */
    @Transactional
    public Activity createActivity(Actor actor, JsonNode activityJson) {
        String type = activityJson.has("type") ? activityJson.get("type").asText() : "Create";
        String baseUrl = actor.getActorId("").replace("/users/" + actor.username, "");
        String activityId = actor.getActorId(baseUrl) + "/activities/" + UUID.randomUUID();

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

                            // Populate PostGIS geometry from GPX track points
                            populateGeometry(activity, gpxData);

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

        // Run analytics in the same transaction
        // These are lightweight calculations that should be part of activity creation
        try {
            // Check for personal records
            personalRecordService.checkForPrs(activity);

            // Calculate splits (per km)
            splitService.calculateSplits(activity, true);

            // Calculate pace zones
            paceZoneService.calculateZones(activity);

            // Calculate comparisons vs user average
            comparisonService.calculateComparisons(activity);
        } catch (Exception e) {
            // Log but don't fail activity creation if analytics fail
            java.util.logging.Logger.getLogger(ActivityService.class.getName())
                .log(java.util.logging.Level.WARNING, "Analytics calculation failed for activity " + activity.id, e);
        }

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
     * Delegates to GpxUtils for shared implementation.
     */
    private JsonNode convertGpxDataToJsonNode(GpxData gpxData) {
        return org.openpace.shared.GpxUtils.convertGpxDataToJsonNode(gpxData);
    }

    /**
     * Populate PostGIS geometry fields from GPX track data.
     * Builds LineString from track points and extracts start/end Points.
     */
    private void populateGeometry(Activity activity, GpxData gpxData) {
        if (gpxData.points == null || gpxData.points.isEmpty()) {
            return;
        }

        org.locationtech.jts.geom.GeometryFactory geometryFactory =
            new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);

        // Build coordinate array for LineString
        org.locationtech.jts.geom.Coordinate[] coordinates =
            new org.locationtech.jts.geom.Coordinate[gpxData.points.size()];

        for (int i = 0; i < gpxData.points.size(); i++) {
            TrackPoint point = gpxData.points.get(i);
            coordinates[i] = new org.locationtech.jts.geom.Coordinate(point.longitude, point.latitude);
        }

        // Create LineString (simplified for large tracks)
        if (coordinates.length >= 2) {
            activity.trackLine = geometryFactory.createLineString(coordinates);

            // Extract start and end points
            activity.startPoint = geometryFactory.createPoint(coordinates[0]);
            activity.endPoint = geometryFactory.createPoint(coordinates[coordinates.length - 1]);
        }
    }
}
