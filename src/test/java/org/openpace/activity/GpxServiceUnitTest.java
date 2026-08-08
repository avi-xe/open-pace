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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GpxService.
 *
 * Tests pure math logic without container dependency.
 */
class GpxServiceUnitTest {

    private GpxService gpxService;

    @BeforeEach
    void setUp() {
        gpxService = new GpxService();
    }

    // ============================================================
    // Haversine Distance Tests
    // ============================================================

    @Test
    void shouldCalculateZeroDistanceForSamePoint() {
        double distance = gpxService.calculateDistance(40.7829, -73.9654, 40.7829, -73.9654);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void shouldCalculateDistanceBetweenNYCLocations() {
        // Central Park (40.7829, -73.9654) to Times Square (40.7580, -73.9855)
        // Approximately 3.2 km
        double distance = gpxService.calculateDistance(40.7829, -73.9654, 40.7580, -73.9855);
        assertTrue(distance > 2800 && distance < 3500,
            "Expected ~3.2km, got " + distance);
    }

    @Test
    void shouldCalculateDistanceAcrossContinents() {
        // New York to London: approximately 5,570 km
        double distance = gpxService.calculateDistance(40.7128, -74.0060, 51.5074, -0.1278);
        assertTrue(distance > 5500000 && distance < 5700000,
            "Expected ~5570km, got " + distance);
    }

    @Test
    void shouldCalculateShortDistanceCorrectly() {
        // Two points 100m apart (approximately)
        double distance = gpxService.calculateDistance(40.7829, -73.9654, 40.7838, -73.9654);
        assertTrue(distance > 90 && distance < 110,
            "Expected ~100m, got " + distance);
    }

    // ============================================================
    // GPX Parsing Tests
    // ============================================================

    @Test
    void shouldReturnNullForNullInput() {
        assertNull(gpxService.parseGpx(null));
    }

    @Test
    void shouldReturnNullForEmptyInput() {
        assertNull(gpxService.parseGpx(""));
        assertNull(gpxService.parseGpx("  "));
    }

    @Test
    void shouldParseValidGpx() {
        String gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <trkseg>
                  <trkpt lat="40.7829" lon="-73.9654">
                    <ele>10</ele>
                  </trkpt>
                  <trkpt lat="40.7840" lon="-73.9660">
                    <ele>12</ele>
                  </trkpt>
                  <trkpt lat="40.7850" lon="-73.9670">
                    <ele>15</ele>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
            """;

        GpxData result = gpxService.parseGpx(gpx);

        assertNotNull(result);
        assertNotNull(result.points);
        assertEquals(3, result.points.size());
        assertNotNull(result.summary);

        // Verify first point
        assertEquals(40.7829, result.points.get(0).latitude, 0.0001);
        assertEquals(-73.9654, result.points.get(0).longitude, 0.0001);
        assertEquals(10.0, result.points.get(0).elevation, 0.1);

        // Verify summary
        assertTrue(result.summary.totalDistance > 0, "Total distance should be positive");
        assertTrue(result.summary.elevationGain > 0, "Elevation gain should be positive");
    }

    @Test
    void shouldParseGpxWithTimestamps() {
        String gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <trkseg>
                  <trkpt lat="40.7829" lon="-73.9654">
                    <ele>10</ele>
                    <time>2024-01-01T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="40.7840" lon="-73.9660">
                    <ele>12</ele>
                    <time>2024-01-01T10:05:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
            """;

        GpxData result = gpxService.parseGpx(gpx);

        assertNotNull(result);
        assertEquals(2, result.points.size());
        assertNotNull(result.points.get(0).timestamp);
        assertNotNull(result.points.get(1).timestamp);
        assertTrue(result.summary.averagePace > 0, "Average pace should be computed from timestamps");
    }

    @Test
    void shouldHandleMultipleSegments() {
        String gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <trkseg>
                  <trkpt lat="40.7829" lon="-73.9654"><ele>10</ele></trkpt>
                  <trkpt lat="40.7840" lon="-73.9660"><ele>12</ele></trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="40.7850" lon="-73.9670"><ele>15</ele></trkpt>
                  <trkpt lat="40.7860" lon="-73.9680"><ele>18</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """;

        GpxData result = gpxService.parseGpx(gpx);

        assertNotNull(result);
        assertEquals(4, result.points.size(), "Should combine points from all segments");
    }

    // ============================================================
    // Elevation Tests
    // ============================================================

    @Test
    void shouldCalculateElevationGainAndLoss() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(40.0, -74.0, 100.0, null, 0),
            new TrackPoint(40.001, -74.0, 150.0, null, 0),  // +50m
            new TrackPoint(40.002, -74.0, 120.0, null, 0),  // -30m
            new TrackPoint(40.003, -74.0, 200.0, null, 0)   // +80m
        );

        double[] changes = gpxService.calculateElevationChanges(points);

        assertEquals(130.0, changes[0], 0.1, "Elevation gain: 50 + 80 = 130m");
        assertEquals(30.0, changes[1], 0.1, "Elevation loss: 30m");
    }

    @Test
    void shouldHandleFlatTrack() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(40.0, -74.0, 100.0, null, 0),
            new TrackPoint(40.001, -74.0, 100.0, null, 0),
            new TrackPoint(40.002, -74.0, 100.0, null, 0)
        );

        double[] changes = gpxService.calculateElevationChanges(points);

        assertEquals(0.0, changes[0], 0.1, "No elevation gain on flat track");
        assertEquals(0.0, changes[1], 0.1, "No elevation loss on flat track");
    }

    @Test
    void shouldHandleEmptyTrack() {
        double[] changes = gpxService.calculateElevationChanges(Collections.emptyList());
        assertEquals(0.0, changes[0], 0.1);
        assertEquals(0.0, changes[1], 0.1);
    }

    // ============================================================
    // GPX Export Tests
    // ============================================================

    @Test
    void shouldExportGpxString() {
        GpxData gpxData = new GpxData();
        gpxData.points = Arrays.asList(
            new TrackPoint(40.7829, -73.9654, 10.0, null, 0),
            new TrackPoint(40.7840, -73.9660, 12.0, null, 0)
        );

        String gpxXml = gpxService.generateGpx("Test Activity", gpxData);

        assertNotNull(gpxXml);
        assertTrue(gpxXml.contains("<?xml version=\"1.0\""));
        assertTrue(gpxXml.contains("Test Activity"));
        assertTrue(gpxXml.contains("lat=\"40.7829\""));
        assertTrue(gpxXml.contains("lon=\"-73.9654\""));
        assertTrue(gpxXml.contains("<ele>10.0</ele>"));
        assertTrue(gpxXml.contains("</gpx>"));
    }
}
