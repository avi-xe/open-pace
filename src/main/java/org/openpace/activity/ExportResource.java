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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import org.openpace.shared.ErrorResponse;

/**
 * REST endpoints for exporting activities as GPX or JSON files.
 */
@Path("/api/activities/{activityId}/export")
@ApplicationScoped
public class ExportResource {

    private static final Logger LOG = Logger.getLogger(ExportResource.class.getName());

    @Inject
    ActivityService activityService;

    @Inject
    GpxService gpxService;

    /**
     * Export activity as GPX file.
     */
    @GET
    @Path("/gpx")
    @Produces("application/gpx+xml")
    public Response exportGpx(@PathParam("activityId") String activityId) {
        Activity activity = Activity.findByActivityId(activityId);
        if (activity == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTIVITY_NOT_FOUND", "Activity '" + activityId + "' not found"))
                .build();
        }

        if (Visibility.PRIVATE.equals(activity.visibility)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("PRIVATE_ACTIVITY", "Cannot export private activity"))
                .build();
        }

        if (activity.trackData == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("NO_TRACK_DATA", "Activity has no track data to export"))
                .build();
        }

        GpxData gpxData = convertTrackDataToGpxData(activity);
        if (gpxData == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("INVALID_TRACK_DATA", "Could not parse track data"))
                .build();
        }

        String activityName = getActivityName(activity);
        String gpxXml = gpxService.generateGpx(activityName, gpxData);
        if (gpxXml == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("EXPORT_FAILED", "Failed to generate GPX"))
                .build();
        }

        String filename = sanitizeFilename(activityName) + ".gpx";
        LOG.info("Exporting GPX for activity: " + activityId);
        return Response.ok(gpxXml, "application/gpx+xml")
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .build();
    }

    /**
     * Export activity as JSON file.
     */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportJson(@PathParam("activityId") String activityId) {
        Activity activity = Activity.findByActivityId(activityId);
        if (activity == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("ACTIVITY_NOT_FOUND", "Activity '" + activityId + "' not found"))
                .build();
        }

        if (Visibility.PRIVATE.equals(activity.visibility)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("PRIVATE_ACTIVITY", "Cannot export private activity"))
                .build();
        }

        JsonNode activityObject = activityService.getActivityObject(activity);
        if (activityObject == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("NO_DATA", "Activity has no data to export"))
                .build();
        }

        String filename = sanitizeFilename(getActivityName(activity)) + ".json";
        LOG.info("Exporting JSON for activity: " + activityId);
        return Response.ok(activityObject, MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .build();
    }

    /**
     * Convert stored trackData JSONB back to a GpxData object.
     */
    private GpxData convertTrackDataToGpxData(Activity activity) {
        try {
            JsonNode trackData = activity.trackData;
            if (trackData == null) {
                return null;
            }

            GpxData gpxData = new GpxData();
            gpxData.points = new java.util.ArrayList<>();

            JsonNode pointsNode = trackData.get("points");
            if (pointsNode != null && pointsNode.isArray()) {
                for (JsonNode p : pointsNode) {
                    TrackPoint point = new TrackPoint();
                    point.latitude = p.get("lat").asDouble();
                    point.longitude = p.get("lon").asDouble();
                    point.elevation = p.has("ele") ? p.get("ele").asDouble() : 0;
                    point.speed = p.has("speed") ? p.get("speed").asDouble() : 0;
                    if (p.has("time") && !p.get("time").isNull()) {
                        point.timestamp = java.time.Instant.parse(p.get("time").asText());
                    }
                    gpxData.points.add(point);
                }
            }

            JsonNode summaryNode = trackData.get("summary");
            if (summaryNode != null) {
                gpxData.summary = new TrackSummary();
                gpxData.summary.totalDistance = summaryNode.has("distance") ? summaryNode.get("distance").asDouble() : 0;
                gpxData.summary.totalDuration = summaryNode.has("duration") ? summaryNode.get("duration").asLong() : 0;
                gpxData.summary.averagePace = summaryNode.has("pace") ? summaryNode.get("pace").asDouble() : 0;
                gpxData.summary.elevationGain = summaryNode.has("elevationGain") ? summaryNode.get("elevationGain").asDouble() : 0;
                gpxData.summary.elevationLoss = summaryNode.has("elevationLoss") ? summaryNode.get("elevationLoss").asDouble() : 0;
                gpxData.summary.maxSpeed = summaryNode.has("maxSpeed") ? summaryNode.get("maxSpeed").asDouble() : 0;
                gpxData.summary.averageSpeed = summaryNode.has("averageSpeed") ? summaryNode.get("averageSpeed").asDouble() : 0;
            }

            return gpxData;
        } catch (Exception e) {
            LOG.warning("Failed to convert track data: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract a display name from the activity.
     */
    private String getActivityName(Activity activity) {
        if (activity.objectJson != null && activity.objectJson.has("name")) {
            return activity.objectJson.get("name").asText();
        }
        return "activity-" + activity.id;
    }

    /**
     * Sanitize a string for use as a filename.
     */
    private String sanitizeFilename(String name) {
        if (name == null) return "activity";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").replaceAll("_+", "_");
    }
}
