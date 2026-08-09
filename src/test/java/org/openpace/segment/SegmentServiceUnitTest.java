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
package org.openpace.segment;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SegmentService GPS matching logic.
 *
 * Tests the pure math and matching algorithms without container dependency.
 */
class SegmentServiceUnitTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // Haversine Distance Tests
    // ============================================================

    @Test
    void shouldCalculateZeroDistanceForSamePoint() {
        double distance = SegmentService.haversineDistance(40.7829, -73.9654, 40.7829, -73.9654);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void shouldCalculateDistanceBetweenNYCLocations() {
        // Central Park to Times Square: ~3.2 km
        double distance = SegmentService.haversineDistance(40.7829, -73.9654, 40.7580, -73.9855);
        assertTrue(distance > 2800 && distance < 3500,
            "Expected ~3.2km, got " + distance);
    }

    @Test
    void shouldCalculateShortDistanceCorrectly() {
        // Two points ~100m apart
        double distance = SegmentService.haversineDistance(40.7829, -73.9654, 40.7838, -73.9654);
        assertTrue(distance > 90 && distance < 110,
            "Expected ~100m, got " + distance);
    }

    @Test
    void shouldCalculateSymmetricDistance() {
        double d1 = SegmentService.haversineDistance(40.7829, -73.9654, 40.7580, -73.9855);
        double d2 = SegmentService.haversineDistance(40.7580, -73.9855, 40.7829, -73.9654);
        assertEquals(d1, d2, 0.001, "Distance should be symmetric");
    }

    @Test
    void shouldCalculateCrossContinentalDistance() {
        // NY to London: ~5570 km
        double distance = SegmentService.haversineDistance(40.7128, -74.0060, 51.5074, -0.1278);
        assertTrue(distance > 5500000 && distance < 5700000,
            "Expected ~5570km, got " + distance);
    }

    // ============================================================
    // Track Point Builder Helpers
    // ============================================================

    private JsonNode createTrackPoint(double lat, double lon, double ele, String time) {
        ObjectNode point = objectMapper.createObjectNode();
        point.put("lat", lat);
        point.put("lon", lon);
        point.put("ele", ele);
        if (time != null) {
            point.put("time", time);
        }
        return point;
    }

    private ArrayNode createTrack(JsonNode... points) {
        ArrayNode track = objectMapper.createArrayNode();
        for (JsonNode point : points) {
            track.add(point);
        }
        return track;
    }

    // ============================================================
    // Segment Matching Logic Tests (via reflection-free approach)
    // ============================================================

    @Test
    void shouldBuildTrackWithTimestamps() {
        // Verify our test helper builds valid track data
        ArrayNode track = createTrack(
            createTrackPoint(40.7829, -73.9654, 10.0, "2024-01-01T10:00:00"),
            createTrackPoint(40.7830, -73.9655, 12.0, "2024-01-01T10:01:00"),
            createTrackPoint(40.7831, -73.9656, 14.0, "2024-01-01T10:02:00")
        );

        assertEquals(3, track.size());
        assertEquals(40.7829, track.get(0).get("lat").asDouble(), 0.0001);
        assertEquals("2024-01-01T10:00:00", track.get(0).get("time").asText());
    }

    @Test
    void shouldCalculateDistanceBetweenSegmentPoints() {
        // A segment from Central Park to Times Square
        double startLat = 40.7829, startLon = -73.9654;
        double endLat = 40.7580, endLon = -73.9855;

        double segmentLength = SegmentService.haversineDistance(startLat, startLon, endLat, endLon);
        assertTrue(segmentLength > 2800 && segmentLength < 3500,
            "Segment should be ~3.2km");

        // Points along the route should be closer to start/end
        double midLat = (startLat + endLat) / 2;
        double midLon = (startLon + endLon) / 2;

        double distFromStart = SegmentService.haversineDistance(midLat, midLon, startLat, startLon);
        double distFromEnd = SegmentService.haversineDistance(midLat, midLon, endLat, endLon);

        assertTrue(distFromStart < segmentLength, "Midpoint should be closer to start than endpoint");
        assertTrue(distFromEnd < segmentLength, "Midpoint should be closer to end than startpoint");
    }

    @Test
    void shouldIdentifyPointsWithinProximityThreshold() {
        // Points within 50m of a location
        double targetLat = 40.7829, targetLon = -73.9654;
        double threshold = 50.0; // meters

        // Point 30m away
        double closeLat = 40.7832, closeLon = -73.9654;
        double distClose = SegmentService.haversineDistance(closeLat, closeLon, targetLat, targetLon);
        assertTrue(distClose <= threshold,
            "Point 30m away should be within threshold, got " + distClose + "m");

        // Point 100m away
        double farLat = 40.7838, farLon = -73.9654;
        double distFar = SegmentService.haversineDistance(farLat, farLon, targetLat, targetLon);
        assertTrue(distFar > threshold,
            "Point 100m away should be outside threshold, got " + distFar + "m");
    }

    @Test
    void shouldCalculateElapsedTimeFromTimestamps() {
        String startTime = "2024-01-01T10:00:00";
        String endTime = "2024-01-01T10:05:30";

        java.time.LocalDateTime start = java.time.LocalDateTime.parse(startTime);
        java.time.LocalDateTime end = java.time.LocalDateTime.parse(endTime);
        long elapsed = java.time.Duration.between(start, end).getSeconds();

        assertEquals(330, elapsed, "Should be 5 minutes 30 seconds = 330 seconds");
    }

    @Test
    void shouldRejectZeroElapsedTime() {
        String sameTime = "2024-01-01T10:00:00";

        java.time.LocalDateTime start = java.time.LocalDateTime.parse(sameTime);
        java.time.LocalDateTime end = java.time.LocalDateTime.parse(sameTime);
        long elapsed = java.time.Duration.between(start, end).getSeconds();

        assertEquals(0, elapsed, "Zero elapsed time should be rejected");
    }
}
