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
package org.openpace.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import org.openpace.activity.GpxData;
import org.openpace.activity.TrackPoint;
import org.openpace.activity.TrackSummary;

/**
 * Shared utilities for GPX data conversion.
 *
 * Consolidates duplicate code from ActivityService, MapResource, and ExportResource.
 */
public final class GpxUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private GpxUtils() {
        // Utility class
    }

    /**
     * Convert GpxData to a JsonNode for JSONB storage.
     */
    public static JsonNode convertGpxDataToJsonNode(GpxData gpxData) {
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

    /**
     * Parse track data from JSONB storage back to GpxData.
     */
    public static GpxData parseTrackData(JsonNode trackData) {
        if (trackData == null) {
            return null;
        }

        GpxData gpxData = new GpxData();
        gpxData.points = new ArrayList<>();

        JsonNode pointsNode = trackData.get("points");
        if (pointsNode != null && pointsNode.isArray()) {
            for (JsonNode pointNode : pointsNode) {
                TrackPoint point = new TrackPoint(
                    pointNode.get("lat").asDouble(),
                    pointNode.get("lon").asDouble(),
                    pointNode.get("ele").asDouble(),
                    null, // timestamp not stored in JSONB
                    pointNode.has("speed") ? pointNode.get("speed").asDouble() : 0
                );
                gpxData.points.add(point);
            }
        }

        JsonNode summaryNode = trackData.get("summary");
        if (summaryNode != null) {
            TrackSummary summary = new TrackSummary();
            summary.totalDistance = summaryNode.has("distance") ? summaryNode.get("distance").asDouble() : 0;
            summary.totalDuration = summaryNode.has("duration") ? summaryNode.get("duration").asLong() : 0;
            summary.averagePace = summaryNode.has("pace") ? summaryNode.get("pace").asDouble() : 0;
            summary.elevationGain = summaryNode.has("elevationGain") ? summaryNode.get("elevationGain").asDouble() : 0;
            summary.elevationLoss = summaryNode.has("elevationLoss") ? summaryNode.get("elevationLoss").asDouble() : 0;
            summary.maxSpeed = summaryNode.has("maxSpeed") ? summaryNode.get("maxSpeed").asDouble() : 0;
            summary.averageSpeed = summaryNode.has("averageSpeed") ? summaryNode.get("averageSpeed").asDouble() : 0;
            gpxData.summary = summary;
        }

        return gpxData;
    }
}
