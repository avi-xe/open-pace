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

import jakarta.enterprise.context.ApplicationScoped;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Service for generating static map images from track data.
 *
 * Fetches OpenStreetMap tiles, composites them into a base map,
 * and draws the track polyline with start/end markers.
 */
@ApplicationScoped
public class MapImageService {

    private static final Logger LOG = Logger.getLogger(MapImageService.class.getName());
    private static final String OSM_TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final int TILE_SIZE = 256;
    private static final int MAP_WIDTH = 800;
    private static final int MAP_HEIGHT = 600;
    private static final int PADDING = 50;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Generate a static map image showing the track.
     *
     * @param gpxData the parsed GPX data with track points
     * @return PNG image bytes, or null on failure
     */
    public byte[] generateMapImage(GpxData gpxData) {
        if (gpxData == null || gpxData.points == null || gpxData.points.size() < 2) {
            return null;
        }

        try {
            // 1. Calculate bounding box with padding
            double latRange = gpxData.maxLat - gpxData.minLat;
            double lonRange = gpxData.maxLon - gpxData.minLon;
            double paddingLat = Math.max(latRange * 0.1, 0.001);
            double paddingLon = Math.max(lonRange * 0.1, 0.001);

            double minLat = gpxData.minLat - paddingLat;
            double maxLat = gpxData.maxLat + paddingLat;
            double minLon = gpxData.minLon - paddingLon;
            double maxLon = gpxData.maxLon + paddingLon;

            // 2. Determine zoom level
            int zoom = calculateZoomLevel(minLat, maxLat, minLon, maxLon);

            // 3. Calculate tile coordinates
            int[] minTile = latLonToTile(minLat, maxLon, zoom); // bottom-left
            int[] maxTile = latLonToTile(maxLat, minLon, zoom); // top-right

            int tilesX = maxTile[0] - minTile[0] + 1;
            int tilesY = minTile[1] - maxTile[1] + 1;

            // 4. Fetch and composite tiles
            BufferedImage baseMap = new BufferedImage(
                tilesX * TILE_SIZE, tilesY * TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gMap = baseMap.createGraphics();
            gMap.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            for (int x = minTile[0]; x <= maxTile[0]; x++) {
                for (int y = maxTile[1]; y <= minTile[1]; y++) {
                    BufferedImage tile = fetchTile(zoom, x, y);
                    if (tile != null) {
                        int px = (x - minTile[0]) * TILE_SIZE;
                        int py = (y - maxTile[1]) * TILE_SIZE;
                        gMap.drawImage(tile, px, py, null);
                    }
                }
            }
            gMap.dispose();

            // 5. Scale to output size
            BufferedImage scaled = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gScaled = scaled.createGraphics();
            gScaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gScaled.drawImage(baseMap, 0, 0, MAP_WIDTH, MAP_HEIGHT, null);
            gScaled.dispose();

            // 6. Draw track overlay
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            // Calculate pixel mapping (scaled coordinates)
            double scaleX = (double) MAP_WIDTH / (tilesX * TILE_SIZE);
            double scaleY = (double) MAP_HEIGHT / (tilesY * TILE_SIZE);

            drawTrack(g, gpxData, zoom, minTile[0], maxTile[1], scaleX, scaleY);

            // 7. Draw start/end markers
            TrackPoint start = gpxData.points.get(0);
            TrackPoint end = gpxData.points.get(gpxData.points.size() - 1);

            Point2D startPixel = latLonToPixel(
                start.latitude, start.longitude, zoom, minTile[0], maxTile[1]);
            Point2D endPixel = latLonToPixel(
                end.latitude, end.longitude, zoom, minTile[0], maxTile[1]);

            drawMarker(g,
                (int) (startPixel.getX() * scaleX),
                (int) (startPixel.getY() * scaleY),
                new Color(34, 139, 34)); // Forest green

            drawMarker(g,
                (int) (endPixel.getX() * scaleX),
                (int) (endPixel.getY() * scaleY),
                Color.RED);

            // 8. Add attribution
            drawAttribution(g, MAP_WIDTH, MAP_HEIGHT);

            g.dispose();

            // 9. Convert to PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to generate map image", e);
            return null;
        }
    }

    /**
     * Calculate appropriate zoom level for the bounding box.
     */
    private int calculateZoomLevel(double minLat, double maxLat,
                                     double minLon, double maxLon) {
        double latDiff = maxLat - minLat;
        double lonDiff = maxLon - minLon;
        double maxDiff = Math.max(latDiff, lonDiff);

        // Start at zoom 12, reduce for larger areas
        if (maxDiff > 2) return 6;
        if (maxDiff > 1) return 7;
        if (maxDiff > 0.5) return 8;
        if (maxDiff > 0.2) return 9;
        if (maxDiff > 0.1) return 10;
        if (maxDiff > 0.05) return 11;
        return 12;
    }

    /**
     * Convert lat/lon to tile coordinates at given zoom level.
     *
     * @return int array [tileX, tileY]
     */
    private int[] latLonToTile(double lat, double lon, int zoom) {
        int x = (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
        int y = (int) Math.floor(
            (1.0 - Math.log(Math.tan(Math.toRadians(lat)) +
            1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0
            * (1 << zoom));
        return new int[]{x, y};
    }

    /**
     * Convert lat/lon to pixel position on the tile grid.
     */
    private Point2D latLonToPixel(double lat, double lon, int zoom,
                                    int originTileX, int originTileY) {
        double n = Math.pow(2, zoom);
        double tileX = (lon + 180.0) / 360.0 * n;
        double latRad = Math.toRadians(lat);
        double tileY = (1.0 - Math.log(Math.tan(latRad) +
            1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

        double pixelX = (tileX - originTileX) * TILE_SIZE;
        double pixelY = (tileY - originTileY) * TILE_SIZE;

        return new Point2D.Double(pixelX, pixelY);
    }

    /**
     * Fetch a single OSM tile.
     */
    private BufferedImage fetchTile(int zoom, int x, int y) {
        try {
            String url = String.format(OSM_TILE_URL, zoom, x, y);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "OpenPace/1.0 (fitness app)")
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to fetch tile: " + zoom + "/" + x + "/" + y, e);
        }
        return null;
    }

    /**
     * Draw track polyline on the map image.
     */
    private void drawTrack(Graphics2D g, GpxData gpxData, int zoom,
                           int originTileX, int originTileY,
                           double scaleX, double scaleY) {
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 100, 200)); // Blue track

        List<Point2D> pixels = new ArrayList<>();
        for (TrackPoint point : gpxData.points) {
            Point2D p = latLonToPixel(point.latitude, point.longitude,
                zoom, originTileX, originTileY);
            pixels.add(new Point2D.Double(p.getX() * scaleX, p.getY() * scaleY));
        }

        for (int i = 1; i < pixels.size(); i++) {
            Point2D prev = pixels.get(i - 1);
            Point2D curr = pixels.get(i);
            g.drawLine(
                (int) prev.getX(), (int) prev.getY(),
                (int) curr.getX(), (int) curr.getY());
        }
    }

    /**
     * Draw a circular marker at the given position.
     */
    private void drawMarker(Graphics2D g, int x, int y, Color color) {
        int radius = 8;
        g.setColor(Color.WHITE);
        g.fillOval(x - radius - 2, y - radius - 2, radius * 2 + 4, radius * 2 + 4);
        g.setColor(color);
        g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
    }

    /**
     * Add OSM attribution text to bottom-right corner.
     * Required by OSM Tile Usage Policy.
     */
    private void drawAttribution(Graphics2D g, int width, int height) {
        String attribution = "\u00A9 OpenStreetMap contributors";

        Font font = new Font("Arial", Font.PLAIN, 10);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(attribution);
        int x = width - textWidth - 10;
        int y = height - 10;

        // White background for readability
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRect(x - 5, y - fm.getAscent() - 2, textWidth + 10, fm.getHeight() + 4);

        g.setColor(Color.BLACK);
        g.drawString(attribution, x, y);
    }
}
