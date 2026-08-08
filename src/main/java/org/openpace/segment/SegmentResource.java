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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import org.openpace.activity.Activity;
import org.openpace.actor.Actor;
import org.openpace.shared.ErrorResponse;

/**
 * REST endpoints for segments and leaderboards.
 */
@Path("/api")
@ApplicationScoped
public class SegmentResource {

    private static final Logger LOG = Logger.getLogger(SegmentResource.class.getName());

    @Inject
    SegmentService segmentService;

    @Inject
    ObjectMapper objectMapper;

    // ── Segment CRUD ─────────────────────────────────────────────────────

    /**
     * Create a new segment.
     *
     * JSON body: name, description, activityType, startLat, startLon, endLat, endLon, distance, actorUsername
     */
    @POST
    @Path("/segments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createSegment(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);

            String name = json.has("name") ? json.get("name").asText() : null;
            String description = json.has("description") ? json.get("description").asText() : null;
            String activityType = json.has("activityType") ? json.get("activityType").asText() : null;
            double startLat = json.has("startLat") ? json.get("startLat").asDouble() : 0;
            double startLon = json.has("startLon") ? json.get("startLon").asDouble() : 0;
            double endLat = json.has("endLat") ? json.get("endLat").asDouble() : 0;
            double endLon = json.has("endLon") ? json.get("endLon").asDouble() : 0;
            double distance = json.has("distance") ? json.get("distance").asDouble() : 0;
            String actorUsername = json.has("actorUsername") ? json.get("actorUsername").asText() : null;

            if (name == null || name.isBlank() || activityType == null || activityType.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_REQUEST", "name and activityType are required"))
                    .build();
            }

            if (actorUsername == null || actorUsername.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_REQUEST", "actorUsername is required"))
                    .build();
            }

            Actor actor = Actor.findByUsername(actorUsername);
            if (actor == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + actorUsername + "' not found"))
                    .build();
            }

            Segment segment = segmentService.createSegment(
                name, description, activityType,
                startLat, startLon, endLat, endLon,
                distance, actor);

            LOG.info("Created segment: " + segment.name + " (id=" + segment.id + ")");
            return Response.status(Response.Status.CREATED).entity(segment).build();

        } catch (Exception e) {
            LOG.warning("Failed to create segment: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_JSON", "Invalid request body"))
                .build();
        }
    }

    /**
     * List all segments, optionally filtered by activity type.
     */
    @GET
    @Path("/segments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listSegments(@QueryParam("activityType") String activityType) {
        List<Segment> segments = segmentService.listSegments(activityType);
        return Response.ok(segments).build();
    }

    /**
     * Get a segment by ID.
     */
    @GET
    @Path("/segments/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSegment(@PathParam("id") Long id) {
        Segment segment = segmentService.getSegment(id);
        if (segment == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("SEGMENT_NOT_FOUND", "Segment '" + id + "' not found"))
                .build();
        }
        return Response.ok(segment).build();
    }

    // ── Segment Efforts ──────────────────────────────────────────────────

    /**
     * Record an effort on a segment.
     *
     * JSON body: activityId, actorUsername, elapsedTime, startedAt
     */
    @POST
    @Path("/segments/{id}/efforts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response recordEffort(@PathParam("id") Long segmentId, String body) {
        try {
            JsonNode json = objectMapper.readTree(body);

            String activityIdStr = json.has("activityId") ? json.get("activityId").asText() : null;
            String actorUsername = json.has("actorUsername") ? json.get("actorUsername").asText() : null;
            long elapsedTime = json.has("elapsedTime") ? json.get("elapsedTime").asLong() : 0;
            String startedAtStr = json.has("startedAt") ? json.get("startedAt").asText() : null;

            if (activityIdStr == null || activityIdStr.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_REQUEST", "activityId is required"))
                    .build();
            }

            if (actorUsername == null || actorUsername.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_REQUEST", "actorUsername is required"))
                    .build();
            }

            Segment segment = segmentService.getSegment(segmentId);
            if (segment == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("SEGMENT_NOT_FOUND", "Segment '" + segmentId + "' not found"))
                    .build();
            }

            Activity activity = Activity.findByActivityId(activityIdStr);
            if (activity == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("ACTIVITY_NOT_FOUND", "Activity '" + activityIdStr + "' not found"))
                    .build();
            }

            Actor actor = Actor.findByUsername(actorUsername);
            if (actor == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("ACTOR_NOT_FOUND", "Actor '" + actorUsername + "' not found"))
                    .build();
            }

            LocalDateTime startedAt = (startedAtStr != null && !startedAtStr.isBlank())
                ? LocalDateTime.parse(startedAtStr)
                : LocalDateTime.now();

            SegmentEffort effort = segmentService.recordEffort(
                segment, activity, actor, elapsedTime, startedAt);

            LOG.info("Recorded effort on segment " + segmentId + " for activity " + activityIdStr);
            return Response.status(Response.Status.CREATED).entity(effort).build();

        } catch (Exception e) {
            LOG.warning("Failed to record effort: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_JSON", "Invalid request body"))
                .build();
        }
    }

    // ── Leaderboards ─────────────────────────────────────────────────────

    /**
     * Get the leaderboard for a specific segment.
     */
    @GET
    @Path("/segments/{id}/leaderboard")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLeaderboard(@PathParam("id") Long segmentId) {
        Segment segment = segmentService.getSegment(segmentId);
        if (segment == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("SEGMENT_NOT_FOUND", "Segment '" + segmentId + "' not found"))
                .build();
        }

        List<SegmentService.LeaderboardEntry> entries = segmentService.getLeaderboard(segmentId);
        return Response.ok(entries).build();
    }

    /**
     * Get the overall leaderboard across all segments of a given activity type.
     */
    @GET
    @Path("/leaderboards/overall")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOverallLeaderboard(@QueryParam("activityType") String activityType) {
        List<SegmentService.OverallLeaderboardEntry> entries =
            segmentService.getOverallLeaderboard(activityType);
        return Response.ok(entries).build();
    }
}
