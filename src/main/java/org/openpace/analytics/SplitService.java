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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.openpace.activity.Activity;

/**
 * Service for calculating per-km or per-mile splits from activity track data.
 */
@ApplicationScoped
@Transactional
public class SplitService {

    private static final Logger LOG = Logger.getLogger(SplitService.class.getName());

    private static final double KM_METERS = 1000.0;
    private static final double MILE_METERS = 1609.344;
    private static final double EARTH_RADIUS = 6371000.0;

    /**
     * Calculate splits for an activity.
     *
     * @param activity the activity with trackData JSONB
     * @param useMetric true for 1km splits, false for 1-mile splits
     * @return list of splits ordered by split number
     */
    public List<ActivitySplit> calculateSplits(Activity activity, boolean useMetric) {
        if (activity.trackData == null) {
            return List.of();
        }

        JsonNode pointsNode = activity.trackData.get("points");
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.isEmpty()) {
            return List.of();
        }

        double splitThreshold = useMetric ? KM_METERS : MILE_METERS;
        double splitDistanceKm = splitThreshold / KM_METERS;

        List<ActivitySplit> splits = new ArrayList<>();

        double accumulatedDistance = 0.0;
        double splitDistance = 0.0;
        long splitElapsedSeconds = 0;
        double splitElevationGain = 0.0;
        double splitElevationLoss = 0.0;
        int splitNumber = 1;

        Instant splitStartTime = parseTime(pointsNode.get(0));
        double prevLat = 0;
        double prevLon = 0;
        double prevEle = 0;
        boolean hasPrev = false;

        for (int i = 0; i < pointsNode.size(); i++) {
            JsonNode point = pointsNode.get(i);

            double lat = point.get("lat").asDouble();
            double lon = point.get("lon").asDouble();
            double ele = point.has("ele") ? point.get("ele").asDouble() : 0;
            Instant currentTime = parseTime(point);

            if (hasPrev) {
                double distance = haversineDistance(prevLat, prevLon, lat, lon);
                splitDistance += distance;
                accumulatedDistance += distance;

                // Elevation gain/loss
                double eleDelta = ele - prevEle;
                if (eleDelta > 0) {
                    splitElevationGain += eleDelta;
                } else {
                    splitElevationLoss += Math.abs(eleDelta);
                }

                // Calculate elapsed time for this point
                if (currentTime != null && splitStartTime != null) {
                    splitElapsedSeconds = currentTime.getEpochSecond() - splitStartTime.getEpochSecond();
                }

                // Check if we've completed a split
                if (splitDistance >= splitThreshold) {
                    double pace = splitElapsedSeconds / splitDistanceKm;

                    ActivitySplit split = new ActivitySplit();
                    split.activity = activity;
                    split.splitNumber = splitNumber;
                    split.distanceMeters = splitDistance;
                    split.elapsedTime = splitElapsedSeconds;
                    split.pace = pace;
                    split.elevationGain = splitElevationGain;
                    split.elevationLoss = splitElevationLoss;

                    splits.add(split);

                    // Reset for next split
                    splitNumber++;
                    splitDistance = 0.0;
                    splitElapsedSeconds = 0;
                    splitElevationGain = 0.0;
                    splitElevationLoss = 0.0;
                    splitStartTime = currentTime;
                }
            }

            prevLat = lat;
            prevLon = lon;
            prevEle = ele;
            hasPrev = true;
        }

        // Create a partial final split if there's remaining distance
        if (splitDistance > 0) {
            double pace = splitElapsedSeconds / splitDistanceKm;

            ActivitySplit split = new ActivitySplit();
            split.activity = activity;
            split.splitNumber = splitNumber;
            split.distanceMeters = splitDistance;
            split.elapsedTime = splitElapsedSeconds;
            split.pace = pace;
            split.elevationGain = splitElevationGain;
            split.elevationLoss = splitElevationLoss;

            splits.add(split);
        }

        // Persist all splits
        for (ActivitySplit split : splits) {
            split.persist();
        }

        LOG.info("Calculated " + splits.size() + " splits for activity " + activity.id);
        return splits;
    }

    /**
     * Calculate distance between two points using the Haversine formula.
     *
     * @return distance in meters
     */
    static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * Parse time from a track point JSON node.
     *
     * @return Instant or null if not available
     */
    static Instant parseTime(JsonNode point) {
        JsonNode timeNode = point.get("time");
        if (timeNode == null || timeNode.isNull()) {
            return null;
        }
        return Instant.parse(timeNode.asText());
    }
}
