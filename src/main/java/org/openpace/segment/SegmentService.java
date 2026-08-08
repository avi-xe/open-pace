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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.openpace.activity.Activity;
import org.openpace.actor.Actor;

/**
 * Service for segment business logic.
 *
 * Handles segment CRUD, matching activities to segments based on GPS tracks,
 * recording segment efforts, and computing leaderboards.
 */
@ApplicationScoped
public class SegmentService {

    private static final Logger LOG = Logger.getLogger(SegmentService.class.getName());

    /** Proximity threshold in meters — a track point must be within this distance of a segment endpoint to count. */
    private static final double PROXIMITY_THRESHOLD_METERS = 200.0;

    @Inject
    SegmentRepository segmentRepository;

    // ── Segment CRUD ─────────────────────────────────────────────────────

    /**
     * Create a new segment.
     */
    public Segment createSegment(String name, String description, String activityType,
                                  double startLat, double startLon,
                                  double endLat, double endLon,
                                  double distance, Actor createdBy) {
        Segment segment = new Segment();
        segment.name = name;
        segment.description = description;
        segment.activityType = activityType;
        segment.startLat = startLat;
        segment.startLon = startLon;
        segment.endLat = endLat;
        segment.endLon = endLon;
        segment.distance = distance;
        segment.createdBy = createdBy;
        segmentRepository.persist(segment);
        LOG.info("Created segment: " + segment.name + " (id=" + segment.id + ")");
        return segment;
    }

    /**
     * Get a segment by ID.
     */
    public Segment getSegment(Long id) {
        return segmentRepository.findById(id);
    }

    /**
     * List all segments, optionally filtered by activity type.
     */
    public List<Segment> listSegments(String activityType) {
        if (activityType != null && !activityType.isBlank()) {
            return segmentRepository.findByActivityType(activityType);
        }
        return segmentRepository.findAll();
    }

    // ── Segment Effort Recording ──────────────────────────────────────────

    /**
     * Match an activity's track data against known segments and record efforts.
     * Called after an activity with GPX data is created.
     *
     * @param activity the activity with trackData JSONB
     * @return list of newly recorded efforts
     */
    public List<SegmentEffort> matchAndRecordEfforts(Activity activity) {
        if (activity.trackData == null) {
            return List.of();
        }

        JsonNode pointsNode = activity.trackData.get("points");
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.isEmpty()) {
            return List.of();
        }

        String activityType = activity.objectType;
        List<Segment> candidates = (activityType != null)
            ? segmentRepository.findByActivityType(activityType)
            : segmentRepository.findAll();

        List<SegmentEffort> recorded = new ArrayList<>();

        for (Segment segment : candidates) {
            // Skip if effort already recorded for this activity+segment
            if (segmentRepository.findBySegmentAndActivity(segment, activity) != null) {
                continue;
            }

            SegmentEffort effort = tryMatchSegment(segment, activity, pointsNode);
            if (effort != null) {
                segmentRepository.persist(effort);
                recorded.add(effort);
                LOG.info("Recorded effort on segment '" + segment.name + "' for activity " + activity.id);
            }
        }

        return recorded;
    }

    /**
     * Manually record an effort on a segment (without GPS matching).
     */
    public SegmentEffort recordEffort(Segment segment, Activity activity, Actor actor,
                                       long elapsedTime, LocalDateTime startedAt) {
        SegmentEffort existing = segmentRepository.findBySegmentAndActivity(segment, activity);
        if (existing != null) {
            return existing; // already recorded
        }

        SegmentEffort effort = new SegmentEffort();
        effort.segment = segment;
        effort.activity = activity;
        effort.actor = actor;
        effort.elapsedTime = elapsedTime;
        effort.startedAt = startedAt;
        segmentRepository.persist(effort);
        return effort;
    }

    // ── Leaderboards ──────────────────────────────────────────────────────

    /**
     * Get the leaderboard for a specific segment.
     * Returns one entry per athlete with their best (fastest) time.
     */
    public List<LeaderboardEntry> getLeaderboard(Long segmentId) {
        Segment segment = segmentRepository.findById(segmentId);
        if (segment == null) {
            return List.of();
        }

        List<SegmentEffort> efforts = segmentRepository.findBySegment(segment);

        // Best effort per actor (efforts already ordered by elapsed_time ASC)
        Map<Long, LeaderboardEntry> bestByActor = new LinkedHashMap<>();
        for (SegmentEffort effort : efforts) {
            Long actorId = effort.actor.id;
            if (!bestByActor.containsKey(actorId)) {
                bestByActor.put(actorId, new LeaderboardEntry(
                    effort.actor.id,
                    effort.actor.username,
                    effort.elapsedTime,
                    effort.activity.id,
                    effort.startedAt
                ));
            }
        }

        List<LeaderboardEntry> entries = new ArrayList<>(bestByActor.values());
        // Rank by fastest time
        entries.sort(Comparator.comparingLong(e -> e.elapsedTime));

        int rank = 1;
        for (LeaderboardEntry entry : entries) {
            entry.rank = rank++;
        }

        return entries;
    }

    /**
     * Get the overall leaderboard across all segments of a given activity type.
     * Ranks athletes by total segments completed, then by total elapsed time.
     */
    public List<OverallLeaderboardEntry> getOverallLeaderboard(String activityType) {
        List<Segment> segments = (activityType != null && !activityType.isBlank())
            ? segmentRepository.findByActivityType(activityType)
            : segmentRepository.findAll();

        // actorId → { totalSegments, totalTime }
        Map<Long, OverallAccumulator> accumulators = new LinkedHashMap<>();

        for (Segment segment : segments) {
            List<SegmentEffort> efforts = segmentRepository.findBySegment(segment);
            // Track best time per actor for this segment
            Map<Long, Long> bestTimeByActor = new LinkedHashMap<>();
            Map<Long, String> usernameByActor = new LinkedHashMap<>();
            Map<Long, Long> lastActivityByActor = new LinkedHashMap<>();

            for (SegmentEffort effort : efforts) {
                Long actorId = effort.actor.id;
                bestTimeByActor.merge(actorId, effort.elapsedTime, Math::min);
                usernameByActor.put(actorId, effort.actor.username);
                lastActivityByActor.put(actorId, effort.activity.id);
            }

            for (Map.Entry<Long, Long> entry : bestTimeByActor.entrySet()) {
                Long actorId = entry.getKey();
                accumulators.merge(actorId,
                    new OverallAccumulator(actorId, usernameByActor.get(actorId), 1, entry.getValue()),
                    (a, b) -> {
                        a.segmentsCompleted += b.segmentsCompleted;
                        a.totalTime += b.totalTime;
                        return a;
                    });
            }
        }

        List<OverallLeaderboardEntry> results = new ArrayList<>();
        for (OverallAccumulator acc : accumulators.values()) {
            results.add(new OverallLeaderboardEntry(
                acc.actorId, acc.username, acc.segmentsCompleted, acc.totalTime));
        }

        results.sort(Comparator
            .comparingInt((OverallLeaderboardEntry e) -> e.segmentsCompleted).reversed()
            .thenComparingLong(e -> e.totalTime));

        int rank = 1;
        for (OverallLeaderboardEntry entry : results) {
            entry.rank = rank++;
        }

        return results;
    }

    // ── Internal Helpers ──────────────────────────────────────────────────

    /**
     * Try to match a segment against an activity's track points.
     * Returns a SegmentEffort if the track passes near both start and end of the segment.
     */
    private SegmentEffort tryMatchSegment(Segment segment, Activity activity, JsonNode pointsNode) {
        boolean reachedStart = false;
        boolean reachedEnd = false;
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;

        for (JsonNode point : pointsNode) {
            double lat = point.get("lat").asDouble();
            double lon = point.get("lon").asDouble();

            if (!reachedStart) {
                double distToStart = haversineDistance(
                    lat, lon, segment.startLat, segment.startLon);
                if (distToStart <= PROXIMITY_THRESHOLD_METERS) {
                    reachedStart = true;
                    startTime = parseTime(point);
                }
            } else if (!reachedEnd) {
                double distToEnd = haversineDistance(
                    lat, lon, segment.endLat, segment.endLon);
                if (distToEnd <= PROXIMITY_THRESHOLD_METERS) {
                    reachedEnd = true;
                    endTime = parseTime(point);
                }
            }
        }

        if (reachedStart && reachedEnd && startTime != null && endTime != null) {
            long elapsed = java.time.Duration.between(startTime, endTime).getSeconds();
            if (elapsed <= 0) {
                return null; // invalid timing
            }

            SegmentEffort effort = new SegmentEffort();
            effort.segment = segment;
            effort.activity = activity;
            effort.actor = activity.actor;
            effort.elapsedTime = elapsed;
            effort.startedAt = startTime;
            return effort;
        }

        return null;
    }

    /**
     * Parse a time field from a track point JSON node.
     */
    private LocalDateTime parseTime(JsonNode point) {
        JsonNode timeNode = point.get("time");
        if (timeNode == null || timeNode.isNull()) {
            return null;
        }
        try {
            return LocalDateTime.parse(timeNode.asText());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Haversine distance between two lat/lon points in meters.
     */
    static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public static class LeaderboardEntry {
        public int rank;
        public Long actorId;
        public String username;
        public long elapsedTime; // seconds
        public Long activityId;
        public LocalDateTime startedAt;

        public LeaderboardEntry(Long actorId, String username, long elapsedTime,
                                 Long activityId, LocalDateTime startedAt) {
            this.actorId = actorId;
            this.username = username;
            this.elapsedTime = elapsedTime;
            this.activityId = activityId;
            this.startedAt = startedAt;
        }
    }

    public static class OverallLeaderboardEntry {
        public int rank;
        public Long actorId;
        public String username;
        public int segmentsCompleted;
        public long totalTime; // seconds

        public OverallLeaderboardEntry(Long actorId, String username,
                                        int segmentsCompleted, long totalTime) {
            this.actorId = actorId;
            this.username = username;
            this.segmentsCompleted = segmentsCompleted;
            this.totalTime = totalTime;
        }
    }

    private static class OverallAccumulator {
        Long actorId;
        String username;
        int segmentsCompleted;
        long totalTime;

        OverallAccumulator(Long actorId, String username, int segmentsCompleted, long totalTime) {
            this.actorId = actorId;
            this.username = username;
            this.segmentsCompleted = segmentsCompleted;
            this.totalTime = totalTime;
        }
    }
}
