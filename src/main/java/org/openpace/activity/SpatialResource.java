package org.openpace.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

/**
 * Spatial query endpoints for geospatial activity searches.
 */
@Path("/api/activities")
@ApplicationScoped
public class SpatialResource {

    private static final Logger LOG = Logger.getLogger(SpatialResource.class.getName());

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ObjectMapper objectMapper;

    @GET
    @Path("/nearby")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNearbyActivities(
            @QueryParam("lat") double latitude,
            @QueryParam("lon") double longitude,
            @QueryParam("radius") double radiusMeters) {
        LOG.info("Nearby query: lat=" + latitude + " lon=" + longitude + " radius=" + radiusMeters);

        if (radiusMeters <= 0 || radiusMeters > 50000) {
            radiusMeters = 1000;
        }

        List<Activity> activities = activityRepository.findNearby(latitude, longitude, radiusMeters);
        LOG.info("Found " + activities.size() + " nearby activities");

        ArrayNode result = objectMapper.createArrayNode();
        for (Activity activity : activities) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", activity.id);
            node.put("activityId", activity.activityId);
            node.put("objectType", activity.objectType);
            node.put("publishedAt", activity.publishedAt != null ? activity.publishedAt.toString() : null);
            if (activity.actor != null) {
                node.put("username", activity.actor.username);
            }
            result.add(node);
        }

        return Response.ok(result).build();
    }

    @GET
    @Path("/bounding-box")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivitiesInBoundingBox(
            @QueryParam("minLat") double minLat,
            @QueryParam("minLon") double minLon,
            @QueryParam("maxLat") double maxLat,
            @QueryParam("maxLon") double maxLon) {
        LOG.info("BoundingBox query: " + minLat + "," + minLon + " to " + maxLat + "," + maxLon);

        List<Activity> activities = activityRepository.findInBoundingBox(minLat, minLon, maxLat, maxLon);
        LOG.info("Found " + activities.size() + " activities in bounding box");

        ArrayNode result = objectMapper.createArrayNode();
        for (Activity activity : activities) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", activity.id);
            node.put("activityId", activity.activityId);
            node.put("objectType", activity.objectType);
            node.put("publishedAt", activity.publishedAt != null ? activity.publishedAt.toString() : null);
            if (activity.actor != null) {
                node.put("username", activity.actor.username);
            }
            result.add(node);
        }

        return Response.ok(result).build();
    }
}
