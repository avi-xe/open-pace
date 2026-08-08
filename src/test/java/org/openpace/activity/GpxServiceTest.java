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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GpxServiceTest {

    @Inject
    GpxService gpxService;

    private static final String SAMPLE_GPX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="OpenPace" xmlns="http://www.topografix.com/GPX/1/1">
          <trk>
            <name>Zurich Lake Run</name>
            <trkseg>
              <trkpt lat="47.365590" lon="8.524997">
                <ele>408</ele>
                <time>2026-08-01T08:00:00Z</time>
              </trkpt>
              <trkpt lat="47.365800" lon="8.525200">
                <ele>412</ele>
                <time>2026-08-01T08:01:00Z</time>
              </trkpt>
              <trkpt lat="47.366000" lon="8.525500">
                <ele>415</ele>
                <time>2026-08-01T08:02:00Z</time>
              </trkpt>
              <trkpt lat="47.366200" lon="8.525800">
                <ele>410</ele>
                <time>2026-08-01T08:03:00Z</time>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
        """;

    @Test
    void shouldParseGpxTrackPoints() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result);
        assertEquals(4, result.points.size());
        assertEquals(47.365590, result.points.get(0).latitude, 0.0001);
        assertEquals(8.524997, result.points.get(0).longitude, 0.0001);
    }

    @Test
    void shouldCalculateElevationChanges() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result.summary);
        // Gain: 408→412 (+4), 412→415 (+3), 415→410 (-5) = 7m gain, 5m loss
        assertEquals(7.0, result.summary.elevationGain, 0.1);
        assertEquals(5.0, result.summary.elevationLoss, 0.1);
    }

    @Test
    void shouldCalculateDistance() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result.summary);
        // Distance between Zurich points should be ~50-100m total
        assertTrue(result.summary.totalDistance > 30,
            "Distance should be > 30m, was: " + result.summary.totalDistance);
        assertTrue(result.summary.totalDistance < 500,
            "Distance should be < 500m, was: " + result.summary.totalDistance);
    }

    @Test
    void shouldCalculateSpeed() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result.summary);
        assertTrue(result.summary.averageSpeed > 0,
            "Average speed should be positive");
        assertTrue(result.summary.maxSpeed >= result.summary.averageSpeed,
            "Max speed should be >= average speed");
    }

    @Test
    void shouldHandleEmptyGpx() {
        String emptyGpx = """
            <?xml version="1.0"?>
            <gpx version="1.1" creator="OpenPace" xmlns="http://www.topografix.com/GPX/1/1"></gpx>
            """;

        GpxData result = gpxService.parseGpx(emptyGpx);
        assertNull(result);
    }

    @Test
    void shouldCalculateBoundingBox() {
        GpxData result = gpxService.parseGpx(SAMPLE_GPX);

        assertNotNull(result);
        assertEquals(47.365590, result.minLat, 0.0001);
        assertEquals(47.366200, result.maxLat, 0.0001);
        assertEquals(8.524997, result.minLon, 0.0001);
        assertEquals(8.525800, result.maxLon, 0.0001);
    }

    @Test
    void shouldCalculateHaversineDistance() {
        // Zurich to Bern ≈ 93 km
        double distance = gpxService.calculateDistance(
            47.3656, 8.5249,  // Zurich
            46.9480, 7.4474   // Bern
        );

        // Should be approximately 93,000 meters
        assertTrue(distance > 80000,
            "Distance should be > 80km, was: " + distance);
        assertTrue(distance < 110000,
            "Distance should be < 110km, was: " + distance);
    }

    @Test
    void shouldGenerateGpx() {
        GpxData gpxData = gpxService.parseGpx(SAMPLE_GPX);
        String gpxXml = gpxService.generateGpx("Test Activity", gpxData);

        assertNotNull(gpxXml);
        assertTrue(gpxXml.contains("<?xml version=\"1.0\""));
        assertTrue(gpxXml.contains("<gpx"));
        assertTrue(gpxXml.contains("<name>Test Activity</name>"));
        assertTrue(gpxXml.contains("<trkpt"));
        assertTrue(gpxData.points.size() > 0);
    }
}
