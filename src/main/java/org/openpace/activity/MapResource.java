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
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * REST endpoints for activity visualization and GPX operations.
 *
 * Provides static map images, elevation profiles, GPX export,
 * and GPX upload for activities.
 */
@Path("/api/activities/{activityId}")
@ApplicationScoped
public class MapResource {

    private static final Logger LOG = Logger.getLogger(MapResource.class.getName());

    @Inject
    ActivityService activityService;

    @Inject
    MapImageService mapImageService;

    @Inject
    GpxService gpxService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Get static map image for an activity.
     * Returns PNG image with track overlaid on OSM tiles.
     */
    @GET
    @Path("/map.png")
    @Produces("image/png")
    public Response getMapImage(@PathParam("activityId") Long activityId) {
        Activity activity = Activity.findById(activityId);
        if (activity == null || activity.trackData == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        GpxData gpxData = parseTrackData(activity.trackData);
        if (gpxData == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        byte[] imageBytes = mapImageService.generateMapImage(gpxData);
        if (imageBytes == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(imageBytes)
            .header("Content-Type", "image/png")
            .header("Cache-Control", "public, max-age=86400")
            .build();
    }

    /**
     * Get elevation profile data for charting.
     * Returns JSON array of {distance, elevation} points.
     */
    @GET
    @Path("/elevation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getElevationProfile(@PathParam("activityId") Long activityId) {
        Activity activity = Activity.findById(activityId);
        if (activity == null || activity.trackData == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        GpxData gpxData = parseTrackData(activity.trackData);
        if (gpxData == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Build elevation profile: cumulative distance vs elevation
        List<Map<String, Double>> profile = new ArrayList<>();
        double cumulativeDistance = 0;

        for (int i = 0; i < gpxData.points.size(); i++) {
            TrackPoint point = gpxData.points.get(i);
            if (i > 0) {
                TrackPoint prev = gpxData.points.get(i - 1);
                cumulativeDistance += gpxService.calculateDistance(
                    prev.latitude, prev.longitude,
                    point.latitude, point.longitude);
            }

            Map<String, Double> entry = new HashMap<>();
            entry.put("distance", cumulativeDistance);
            entry.put("elevation", point.elevation);
            profile.add(entry);
        }

        return Response.ok(profile).build();
    }

    /**
     * Export activity as GPX file.
     * Returns GPX XML with original or reconstructed track data.
     */
    @GET
    @Path("/export.gpx")
    @Produces("application/gpx+xml")
    public Response exportGpx(@PathParam("activityId") Long activityId) {
        Activity activity = Activity.findById(activityId);
        if (activity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String gpxXml;
        if (activity.gpxData != null) {
            // Return original GPX if uploaded
            gpxXml = activity.gpxData;
        } else if (activity.trackData != null) {
            // Generate GPX from stored track data
            GpxData gpxData = parseTrackData(activity.trackData);
            String activityName = getActivityName(activity);
            gpxXml = gpxService.generateGpx(activityName, gpxData);
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (gpxXml == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(gpxXml)
            .header("Content-Type", "application/gpx+xml")
            .header("Content-Disposition",
                "attachment; filename=\"activity-" + activityId + ".gpx\"")
            .build();
    }

    /**
     * Upload GPX file for an existing activity.
     * Parses the GPX and stores track data.
     */
    @POST
    @Path("/gpx")
    @Consumes("application/gpx+xml")
    @Transactional
    public Response uploadGpx(@PathParam("activityId") Long activityId,
                              String gpxXml) {
        Activity activity = Activity.findById(activityId);
        if (activity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Parse GPX
        GpxData gpxData = gpxService.parseGpx(gpxXml);
        if (gpxData == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"INVALID_GPX\", \"message\": \"No track data found in GPX\"}")
                .build();
        }

        // Store raw GPX and parsed data
        activity.gpxData = gpxXml;
        activity.trackData = convertGpxDataToJsonNode(gpxData);
        activity.persist();

        return Response.ok(activity).build();
    }

    /**
     * Parse trackData JSONB into GpxData object.
     * Delegates to GpxUtils for shared implementation.
     */
    private GpxData parseTrackData(JsonNode trackData) {
        return org.openpace.shared.GpxUtils.parseTrackData(trackData);
    }

    /**
     * Get activity name from objectJson or fallback.
     */
    private String getActivityName(Activity activity) {
        if (activity.objectJson != null && activity.objectJson.has("name")) {
            return activity.objectJson.get("name").asText();
        }
        return "Activity " + activity.id;
    }

    /**
     * Convert GpxData to a JsonNode for JSONB storage.
     */
    private JsonNode convertGpxDataToJsonNode(GpxData gpxData) {
        return org.openpace.shared.GpxUtils.convertGpxDataToJsonNode(gpxData);
    }
}
