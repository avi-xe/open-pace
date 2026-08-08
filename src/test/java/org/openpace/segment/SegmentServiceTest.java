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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpace.activity.Activity;
import org.openpace.actor.Actor;

@QuarkusTest
class SegmentServiceTest {

    @Inject
    SegmentService segmentService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    @TestTransaction
    void shouldCreateSegment() {
        Actor actor = new Actor("segsvc-alice", "Alice");
        actor.persist();

        Segment segment = segmentService.createSegment(
            "Heartbreak Hill", "Classic Boston Marathon climb",
            "Run",
            42.3400, -71.0800,
            42.3450, -71.0750,
            1200.0, actor);

        assertNotNull(segment);
        assertNotNull(segment.id);
        assertEquals("Heartbreak Hill", segment.name);
        assertEquals("Classic Boston Marathon climb", segment.description);
        assertEquals("Run", segment.activityType);
        assertEquals(42.3400, segment.startLat);
        assertEquals(-71.0800, segment.startLon);
        assertEquals(42.3450, segment.endLat);
        assertEquals(-71.0750, segment.endLon);
        assertEquals(1200.0, segment.distance);
        assertEquals(actor.id, segment.createdBy.id);
    }

    @Test
    @TestTransaction
    void shouldListAllSegments() {
        Actor actor = new Actor("segsvc-bob", "Bob");
        actor.persist();

        segmentService.createSegment("Segment A", "First", "Run",
            42.3, -71.0, 42.31, -71.01, 1000.0, actor);
        segmentService.createSegment("Segment B", "Second", "Run",
            42.32, -71.02, 42.33, -71.03, 2000.0, actor);

        List<Segment> all = segmentService.listSegments(null);
        assertTrue(all.size() >= 2, "Should list at least 2 segments");
        assertTrue(all.stream().anyMatch(s -> "Segment A".equals(s.name)));
        assertTrue(all.stream().anyMatch(s -> "Segment B".equals(s.name)));
    }

    @Test
    @TestTransaction
    void shouldListSegmentsByActivityType() {
        Actor actor = new Actor("segsvc-carol", "Carol");
        actor.persist();

        segmentService.createSegment("Run Segment", "A running segment", "Run",
            42.3, -71.0, 42.31, -71.01, 1000.0, actor);
        segmentService.createSegment("Ride Segment", "A cycling segment", "Ride",
            42.32, -71.02, 42.33, -71.03, 5000.0, actor);

        List<Segment> runs = segmentService.listSegments("Run");
        assertTrue(runs.stream().allMatch(s -> "Run".equals(s.activityType)));
        assertTrue(runs.stream().anyMatch(s -> "Run Segment".equals(s.name)));

        List<Segment> rides = segmentService.listSegments("Ride");
        assertTrue(rides.stream().allMatch(s -> "Ride".equals(s.activityType)));
        assertTrue(rides.stream().anyMatch(s -> "Ride Segment".equals(s.name)));
    }

    @Test
    @TestTransaction
    void shouldRecordEffortManually() throws Exception {
        Actor actor = new Actor("segsvc-dave", "Dave");
        actor.persist();

        Segment segment = segmentService.createSegment("Hill Climb", "Steep climb", "Run",
            42.34, -71.08, 42.35, -71.07, 800.0, actor);

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = "Create";
        activity.activityId = "segsvc-act-1";
        activity.objectType = "Run";
        activity.publishedAt = LocalDateTime.now();
        activity.persist();

        SegmentEffort effort = segmentService.recordEffort(
            segment, activity, actor, 300L, LocalDateTime.of(2024, 6, 15, 8, 0));

        assertNotNull(effort);
        assertNotNull(effort.id);
        assertEquals(segment.id, effort.segment.id);
        assertEquals(activity.id, effort.activity.id);
        assertEquals(actor.id, effort.actor.id);
        assertEquals(300L, effort.elapsedTime);
    }

    @Test
    @TestTransaction
    void shouldGetLeaderboard() {
        Actor actor1 = new Actor("segsvc-eve", "Eve");
        actor1.persist();
        Actor actor2 = new Actor("segsvc-frank", "Frank");
        actor2.persist();

        Segment segment = segmentService.createSegment("Speed Run", "Fast segment", "Run",
            42.34, -71.08, 42.35, -71.07, 1000.0, actor1);

        Activity act1 = new Activity();
        act1.actor = actor1;
        act1.activityType = "Create";
        act1.activityId = "segsvc-act-2";
        act1.objectType = "Run";
        act1.publishedAt = LocalDateTime.now();
        act1.persist();

        Activity act2 = new Activity();
        act2.actor = actor2;
        act2.activityType = "Create";
        act2.activityId = "segsvc-act-3";
        act2.objectType = "Run";
        act2.publishedAt = LocalDateTime.now();
        act2.persist();

        // Eve: 200 seconds (faster)
        segmentService.recordEffort(segment, act1, actor1, 200L,
            LocalDateTime.of(2024, 7, 1, 9, 0));
        // Frank: 250 seconds (slower)
        segmentService.recordEffort(segment, act2, actor2, 250L,
            LocalDateTime.of(2024, 7, 1, 9, 5));

        List<SegmentService.LeaderboardEntry> leaderboard =
            segmentService.getLeaderboard(segment.id);

        assertEquals(2, leaderboard.size());
        // Eve should be rank 1 (fastest)
        assertEquals(1, leaderboard.get(0).rank);
        assertEquals("segsvc-eve", leaderboard.get(0).username);
        assertEquals(200L, leaderboard.get(0).elapsedTime);
        // Frank should be rank 2
        assertEquals(2, leaderboard.get(1).rank);
        assertEquals("segsvc-frank", leaderboard.get(1).username);
        assertEquals(250L, leaderboard.get(1).elapsedTime);
    }

    @Test
    @TestTransaction
    void shouldReturnEmptyLeaderboardForNonexistentSegment() {
        List<SegmentService.LeaderboardEntry> leaderboard =
            segmentService.getLeaderboard(999999L);

        assertNotNull(leaderboard);
        assertTrue(leaderboard.isEmpty());
    }

    @Test
    @TestTransaction
    void shouldNotDuplicateEffort() {
        Actor actor = new Actor("segsvc-grace", "Grace");
        actor.persist();

        Segment segment = segmentService.createSegment("Dup Test", "No dups", "Run",
            42.34, -71.08, 42.35, -71.07, 500.0, actor);

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = "Create";
        activity.activityId = "segsvc-act-4";
        activity.objectType = "Run";
        activity.publishedAt = LocalDateTime.now();
        activity.persist();

        SegmentEffort first = segmentService.recordEffort(
            segment, activity, actor, 180L, LocalDateTime.of(2024, 8, 1, 7, 0));
        SegmentEffort second = segmentService.recordEffort(
            segment, activity, actor, 180L, LocalDateTime.of(2024, 8, 1, 7, 0));

        // recordEffort returns existing effort for duplicates
        assertEquals(first.id, second.id);

        List<SegmentService.LeaderboardEntry> leaderboard =
            segmentService.getLeaderboard(segment.id);
        assertEquals(1, leaderboard.size(), "Only one effort should exist");
    }
}
