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

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.TrackPoint;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for parsing GPX files and extracting track data.
 *
 * Handles GPX XML parsing, distance calculation (Haversine),
 * elevation gain/loss, and speed computation from timestamps.
 */
@ApplicationScoped
public class GpxService {

    private static final Logger LOG = Logger.getLogger(GpxService.class.getName());
    private static final double EARTH_RADIUS = 6371000; // meters

    /**
     * Parse GPX XML string and extract track data.
     *
     * @param gpxXml the GPX XML string
     * @return parsed GpxData with points and summary, or null if no track data found
     */
    public GpxData parseGpx(String gpxXml) {
        if (gpxXml == null || gpxXml.isBlank()) {
            return null;
        }

        try {
            GPX gpx = GPX.read(new StringReader(gpxXml));
            List<Track> tracks = gpx.tracks();

            if (tracks.isEmpty()) {
                return null;
            }

            // Collect all track points from all tracks/segments
            List<org.openpace.activity.TrackPoint> allPoints = new ArrayList<>();

            for (Track track : tracks) {
                for (TrackSegment segment : track.segments()) {
                    List<TrackPoint> jpxPoints = segment.points();
                    for (int i = 0; i < jpxPoints.size(); i++) {
                        TrackPoint jpxPoint = jpxPoints.get(i);
                        org.openpace.activity.TrackPoint point = convertTrackPoint(jpxPoint);

                        // Calculate speed from previous point
                        if (!allPoints.isEmpty() && point.timestamp != null) {
                            org.openpace.activity.TrackPoint prev = allPoints.get(allPoints.size() - 1);
                            if (prev.timestamp != null) {
                                double distance = calculateDistance(
                                        prev.latitude, prev.longitude,
                                        point.latitude, point.longitude);
                                long timeDiff = Duration.between(prev.timestamp, point.timestamp).getSeconds();
                                if (timeDiff > 0) {
                                    point.speed = distance / timeDiff;
                                }
                            }
                        }

                        allPoints.add(point);
                    }
                }
            }

            if (allPoints.isEmpty()) {
                return null;
            }

            // Calculate summary statistics
            TrackSummary summary = calculateSummary(allPoints);

            // Calculate bounding box
            double minLat = allPoints.stream().mapToDouble(p -> p.latitude).min().orElse(0);
            double maxLat = allPoints.stream().mapToDouble(p -> p.latitude).max().orElse(0);
            double minLon = allPoints.stream().mapToDouble(p -> p.longitude).min().orElse(0);
            double maxLon = allPoints.stream().mapToDouble(p -> p.longitude).max().orElse(0);

            GpxData gpxData = new GpxData();
            gpxData.points = allPoints;
            gpxData.summary = summary;
            gpxData.minLat = minLat;
            gpxData.maxLat = maxLat;
            gpxData.minLon = minLon;
            gpxData.maxLon = maxLon;

            return gpxData;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse GPX data", e);
            return null;
        }
    }

    /**
     * Convert a jpx TrackPoint to our TrackPoint model.
     */
    private org.openpace.activity.TrackPoint convertTrackPoint(TrackPoint jpxPoint) {
        double lat = jpxPoint.latitude().orElseThrow().doubleValue();
        double lon = jpxPoint.longitude().orElseThrow().doubleValue();
        double ele = jpxPoint.elevation().orElse(0.0).doubleValue();
        Instant time = jpxPoint.time().orElse(null);

        return new org.openpace.activity.TrackPoint(lat, lon, ele, time, 0);
    }

    /**
     * Calculate summary statistics from track points.
     */
    private TrackSummary calculateSummary(List<org.openpace.activity.TrackPoint> points) {
        TrackSummary summary = new TrackSummary();

        double totalDistance = 0;
        double elevationGain = 0;
        double elevationLoss = 0;
        double maxSpeed = 0;
        double totalSpeed = 0;
        int speedCount = 0;

        for (int i = 0; i < points.size(); i++) {
            org.openpace.activity.TrackPoint point = points.get(i);

            // Calculate distance from previous point
            if (i > 0) {
                org.openpace.activity.TrackPoint prev = points.get(i - 1);
                totalDistance += calculateDistance(
                        prev.latitude, prev.longitude,
                        point.latitude, point.longitude);

                // Elevation changes
                double eleDiff = point.elevation - prev.elevation;
                if (eleDiff > 0) {
                    elevationGain += eleDiff;
                } else {
                    elevationLoss += Math.abs(eleDiff);
                }
            }

            // Track speed
            if (point.speed > 0) {
                maxSpeed = Math.max(maxSpeed, point.speed);
                totalSpeed += point.speed;
                speedCount++;
            }
        }

        // Calculate duration
        long duration = 0;
        if (points.size() >= 2) {
            org.openpace.activity.TrackPoint first = points.get(0);
            org.openpace.activity.TrackPoint last = points.get(points.size() - 1);
            if (first.timestamp != null && last.timestamp != null) {
                duration = Duration.between(first.timestamp, last.timestamp).getSeconds();
            }
        }

        summary.totalDistance = totalDistance;
        summary.totalDuration = duration;
        summary.elevationGain = elevationGain;
        summary.elevationLoss = elevationLoss;
        summary.maxSpeed = maxSpeed;
        summary.averageSpeed = speedCount > 0 ? totalSpeed / speedCount : 0;

        // Pace: seconds per kilometer (only meaningful for running/walking)
        if (totalDistance > 0 && duration > 0) {
            summary.averagePace = (duration / totalDistance) * 1000;
        } else {
            summary.averagePace = 0;
        }

        return summary;
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
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Calculate elevation gain and loss from track points.
     *
     * @return double array [gain, loss] in meters
     */
    public double[] calculateElevationChanges(List<org.openpace.activity.TrackPoint> points) {
        double gain = 0;
        double loss = 0;

        for (int i = 1; i < points.size(); i++) {
            double diff = points.get(i).elevation - points.get(i - 1).elevation;
            if (diff > 0) {
                gain += diff;
            } else {
                loss += Math.abs(diff);
            }
        }

        return new double[]{gain, loss};
    }

    /**
     * Calculate average pace from distance and duration.
     * Pace is seconds per kilometer.
     *
     * @param distanceMeters distance in meters
     * @param durationSeconds duration in seconds
     * @return pace in seconds per kilometer
     */
    public double calculatePace(double distanceMeters, long durationSeconds) {
        if (distanceMeters <= 0) {
            return 0;
        }
        return (durationSeconds / distanceMeters) * 1000;
    }

    /**
     * Generate GPX XML from track data for export.
     *
     * @param name activity name
     * @param gpxData the parsed track data
     * @return GPX XML string
     */
    public String generateGpx(String name, GpxData gpxData) {
        if (gpxData == null || gpxData.points == null || gpxData.points.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"Open Pace\"\n");
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n");
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");

        if (name != null && !name.isBlank()) {
            sb.append("  <name>").append(escapeXml(name)).append("</name>\n");
        }

        sb.append("  <trk>\n");
        if (name != null && !name.isBlank()) {
            sb.append("    <name>").append(escapeXml(name)).append("</name>\n");
        }
        sb.append("    <trkseg>\n");

        for (org.openpace.activity.TrackPoint point : gpxData.points) {
            sb.append("      <trkpt lat=\"").append(point.latitude).append("\" lon=\"")
              .append(point.longitude).append("\">\n");
            sb.append("        <ele>").append(point.elevation).append("</ele>\n");
            if (point.timestamp != null) {
                sb.append("        <time>").append(point.timestamp.toString()).append("</time>\n");
            }
            sb.append("      </trkpt>\n");
        }

        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>\n");

        return sb.toString();
    }

    /**
     * Escape XML special characters.
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
