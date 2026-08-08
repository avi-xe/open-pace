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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openpace.activity.Activity;

/**
 * Service for detecting and tracking personal records.
 *
 * Compares activity segment times against known PR distance thresholds
 * to determine if a new personal record has been achieved.
 */
@ApplicationScoped
@Transactional
public class PersonalRecordService {

    private static final double EARTH_RADIUS = 6371000; // meters

    // PR distance thresholds per activity type
    private static final Map<String, List<PrDistance>> PR_DISTANCES = Map.of(
        "Run", List.of(
            new PrDistance("1K", 1000),
            new PrDistance("5K", 5000),
            new PrDistance("10K", 10000),
            new PrDistance("Half Marathon", 21097.5),
            new PrDistance("Marathon", 42195)
        ),
        "Ride", List.of(
            new PrDistance("20K", 20000),
            new PrDistance("50K", 50000),
            new PrDistance("100K", 100000),
            new PrDistance("200K", 200000)
        ),
        "Swim", List.of(
            new PrDistance("400m", 400),
            new PrDistance("1500m", 1500),
            new PrDistance("5K", 5000)
        )
    );

    /**
     * Check for personal records after activity creation.
     *
     * Parses track points, calculates segment times for each PR distance threshold,
     * and persists any new personal records.
     *
     * @param activity the activity to check for PRs
     * @return list of newly achieved personal records (may be empty)
     */
    public List<PersonalRecord> checkForPrs(Activity activity) {
        List<PersonalRecord> newPrs = new ArrayList<>();

        JsonNode trackData = activity.trackData;
        if (trackData == null || !trackData.has("points")) {
            return newPrs;
        }

        String activityType = activity.activityType;
        List<PrDistance> distances = PR_DISTANCES.get(activityType);
        if (distances == null) {
            return newPrs;
        }

        Long actorId = activity.actor.id;

        for (PrDistance prDistance : distances) {
            long segmentTime = extractSegmentTime(trackData.get("points"), prDistance.meters);
            if (segmentTime <= 0) {
                continue; // activity didn't cover this distance
            }

            // Check if existing PR exists
            PersonalRecord existingPr = PersonalRecord.findByActorAndTypeAndDistance(
                    actorId, activityType, prDistance.label);

            boolean isNewRecord = false;
            if (existingPr == null) {
                isNewRecord = true;
            } else if (segmentTime < existingPr.elapsedTime) {
                // Faster time = new record
                isNewRecord = true;
            } else if (segmentTime == existingPr.elapsedTime
                    && activity.publishedAt.isBefore(existingPr.achievedAt)) {
                // Tie goes to earlier activity
                isNewRecord = true;
            }

            if (isNewRecord) {
                PersonalRecord pr;
                if (existingPr != null) {
                    // Update existing record
                    pr = existingPr;
                } else {
                    // Create new record
                    pr = new PersonalRecord();
                    pr.actor = activity.actor;
                    pr.activityType = activityType;
                    pr.distanceLabel = prDistance.label;
                    pr.distanceMeters = prDistance.meters;
                }
                pr.elapsedTime = segmentTime;
                pr.activity = activity;
                pr.achievedAt = activity.publishedAt;
                pr.updatedAt = LocalDateTime.now();
                if (existingPr == null) {
                    pr.persist();
                }
                // Existing records are managed in the transaction — dirty checking persists changes
                newPrs.add(pr);
            }
        }

        return newPrs;
    }

    /**
     * Extract the elapsed time to cover a target distance from track points.
     *
     * @param pointsNode the JSON array of track points
     * @param targetDistance the target distance in meters
     * @return elapsed seconds to reach target distance, or -1 if not reached
     */
    public long extractSegmentTime(JsonNode pointsNode, double targetDistance) {
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.size() < 2) {
            return -1;
        }

        double accumulatedDistance = 0;
        Instant startTime = null;

        for (int i = 0; i < pointsNode.size(); i++) {
            JsonNode point = pointsNode.get(i);
            double lat = point.get("lat").asDouble();
            double lon = point.get("lon").asDouble();

            if (i == 0) {
                // Parse start time from first point
                JsonNode timeNode = point.get("time");
                if (timeNode == null || timeNode.isNull()) {
                    return -1;
                }
                startTime = Instant.parse(timeNode.asText());
                continue;
            }

            // Get previous point
            JsonNode prevPoint = pointsNode.get(i - 1);
            double prevLat = prevPoint.get("lat").asDouble();
            double prevLon = prevPoint.get("lon").asDouble();

            accumulatedDistance += haversineDistance(prevLat, prevLon, lat, lon);

            if (accumulatedDistance >= targetDistance) {
                // Interpolate the exact point where distance threshold is crossed
                JsonNode timeNode = point.get("time");
                if (timeNode == null || timeNode.isNull()) {
                    return -1;
                }
                Instant currentTime = Instant.parse(timeNode.asText());
                JsonNode prevTimeNode = prevPoint.get("time");
                Instant prevTime = prevTimeNode != null && !prevTimeNode.isNull()
                        ? Instant.parse(prevTimeNode.asText()) : startTime;

                double segmentDistFromPrev = accumulatedDistance - targetDistance;
                double totalDistFromPrev = haversineDistance(prevLat, prevLon, lat, lon);
                long timeDiff = currentTime.getEpochSecond() - prevTime.getEpochSecond();

                // Linear interpolation
                long interpolatedSeconds = currentTime.getEpochSecond()
                        - (long) (segmentDistFromPrev / totalDistFromPrev * timeDiff)
                        - startTime.getEpochSecond();

                return Math.max(interpolatedSeconds, 1);
            }
        }

        return -1; // target distance not reached
    }

    /**
     * Calculate distance between two coordinates using Haversine formula.
     *
     * @param lat1 latitude of first point (degrees)
     * @param lon1 longitude of first point (degrees)
     * @param lat2 latitude of second point (degrees)
     * @param lon2 longitude of second point (degrees)
     * @return distance in meters
     */
    public double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Inner class representing a PR distance threshold.
     */
    public static class PrDistance {
        public final String label;
        public final double meters;

        public PrDistance(String label, double meters) {
            this.label = label;
            this.meters = meters;
        }
    }
}
