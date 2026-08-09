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
 * Service for calculating pace zones from activity track data.
 * Pace zones categorize effort based on instantaneous pace (seconds per km).
 */
@ApplicationScoped
@Transactional
public class PaceZoneService {

    private static final Logger LOG = Logger.getLogger(PaceZoneService.class.getName());

    private static final double KM_METERS = 1000.0;
    private static final double EARTH_RADIUS = 6371000.0;

    /**
     * Pace zone definitions for Run/Walk/Hike (seconds per km).
     * Lower pace = faster speed.
     * Zone 1: Recovery — pace > 300 (slower than 5:00/km)
     * Zone 2: Easy — 270-300 (4:30-5:00/km)
     * Zone 3: Tempo — 240-270 (4:00-4:30/km)
     * Zone 4: Threshold — 210-240 (3:30-4:00/km)
     * Zone 5: VO2 Max — pace < 210 (faster than 3:30/km)
     */
    private static final PaceZone[] RUN_ZONES = {
        new PaceZone(1, "Recovery", 300, Double.MAX_VALUE),
        new PaceZone(2, "Easy", 270, 300),
        new PaceZone(3, "Tempo", 240, 270),
        new PaceZone(4, "Threshold", 210, 240),
        new PaceZone(5, "VO2 Max", 0, 210)
    };

    /**
     * Calculate pace zones for an activity.
     *
     * @param activity the activity with trackData JSONB
     * @return list of pace zones ordered by zone number
     */
    public List<ActivityPaceZone> calculateZones(Activity activity) {
        if (activity.trackData == null) {
            return List.of();
        }

        JsonNode pointsNode = activity.trackData.get("points");
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.isEmpty()) {
            return List.of();
        }

        PaceZone[] zones = getZonesForType(activity.activityType);
        long[] zoneTime = new long[zones.length];

        double prevLat = 0;
        double prevLon = 0;
        Instant prevTime = null;
        boolean hasPrev = false;

        for (int i = 0; i < pointsNode.size(); i++) {
            JsonNode point = pointsNode.get(i);

            double lat = point.get("lat").asDouble();
            double lon = point.get("lon").asDouble();
            Instant currentTime = parseTime(point);

            if (hasPrev && prevTime != null && currentTime != null) {
                double distance = haversineDistance(prevLat, prevLon, lat, lon);
                long timeDiff = currentTime.getEpochSecond() - prevTime.getEpochSecond();

                if (distance > 0 && timeDiff > 0) {
                    double pace = calculatePace(distance, timeDiff);
                    int zoneIndex = findZoneIndex(pace, zones);
                    if (zoneIndex >= 0) {
                        zoneTime[zoneIndex] += timeDiff;
                    }
                }
            }

            prevLat = lat;
            prevLon = lon;
            prevTime = currentTime;
            hasPrev = true;
        }

        // Calculate total time and percentages
        long totalTime = 0;
        for (long time : zoneTime) {
            totalTime += time;
        }

        List<ActivityPaceZone> result = new ArrayList<>();
        for (int i = 0; i < zones.length; i++) {
            if (zoneTime[i] > 0) {
                ActivityPaceZone paceZone = new ActivityPaceZone();
                paceZone.activity = activity;
                paceZone.zoneNumber = zones[i].number;
                paceZone.zoneName = zones[i].name;
                paceZone.timeInSeconds = zoneTime[i];
                paceZone.percentage = totalTime > 0 ? (zoneTime[i] * 100.0 / totalTime) : 0;

                paceZone.persist();
                result.add(paceZone);
            }
        }

        LOG.info("Calculated " + result.size() + " pace zones for activity " + activity.id);
        return result;
    }

    /**
     * Calculate instantaneous pace between two points.
     *
     * @param distanceMeters distance between points in meters
     * @param timeSeconds time between points in seconds
     * @return pace in seconds per km
     */
    private double calculatePace(double distanceMeters, long timeSeconds) {
        return timeSeconds / (distanceMeters / KM_METERS);
    }

    /**
     * Find the zone index for a given pace.
     *
     * @param pace pace in seconds per km
     * @param zones zone definitions
     * @return zone index, or -1 if no zone matches
     */
    private int findZoneIndex(double pace, PaceZone[] zones) {
        for (int i = 0; i < zones.length; i++) {
            if (pace >= zones[i].minPace && pace < zones[i].maxPace) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get zone definitions for activity type.
     */
    private PaceZone[] getZonesForType(String activityType) {
        if ("Run".equals(activityType) || "Walk".equals(activityType) || "Hike".equals(activityType)) {
            return RUN_ZONES;
        }
        // Default: use run zones for other activity types
        return RUN_ZONES;
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

    /**
     * Inner class for zone configuration.
     */
    public static class PaceZone {
        public final int number;
        public final String name;
        public final double minPace;  // seconds per km (lower = faster)
        public final double maxPace;

        public PaceZone(int number, String name, double minPace, double maxPace) {
            this.number = number;
            this.name = name;
            this.minPace = minPace;
            this.maxPace = maxPace;
        }
    }
}
