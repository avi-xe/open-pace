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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.openpace.activity.Activity;
import org.openpace.actor.Actor;

@QuarkusTest
class SegmentResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldCreateSegment() throws Exception {
        tx.begin();
        Actor actor = new Actor("segres-alice", "Alice");
        actor.persist();
        tx.commit();

        String json = """
            {
              "name": "Test Hill",
              "description": "A steep climb",
              "activityType": "Run",
              "startLat": 42.3601,
              "startLon": -71.0589,
              "endLat": 42.3610,
              "endLon": -71.0580,
              "distance": 500,
              "actorUsername": "segres-alice"
            }
            """;

        given()
            .contentType("application/json")
            .body(json)
        .when()
            .post("/api/segments")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Test Hill"))
            .body("description", equalTo("A steep climb"))
            .body("activityType", equalTo("Run"))
            .body("startLat", equalTo(42.3601f))
            .body("startLon", equalTo(-71.0589f))
            .body("endLat", equalTo(42.3610f))
            .body("endLon", equalTo(-71.0580f))
            .body("distance", equalTo(500f));
    }

    @Test
    void shouldListSegments() throws Exception {
        tx.begin();
        Actor actor = new Actor("segres-bob", "Bob");
        actor.persist();

        Segment segment = new Segment();
        segment.name = "Listed Segment";
        segment.description = "For listing test";
        segment.activityType = "Ride";
        segment.startLat = 40.0;
        segment.startLon = -74.0;
        segment.endLat = 40.1;
        segment.endLon = -74.1;
        segment.distance = 1000.0;
        segment.createdBy = actor;
        segment.persist();
        tx.commit();

        given()
            .contentType("application/json")
        .when()
            .get("/api/segments")
        .then()
            .statusCode(200)
            .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldGetSegment() throws Exception {
        tx.begin();
        Actor actor = new Actor("segres-carol", "Carol");
        actor.persist();

        Segment segment = new Segment();
        segment.name = "Fetchable Hill";
        segment.description = "A known segment";
        segment.activityType = "Run";
        segment.startLat = 51.5;
        segment.startLon = -0.1;
        segment.endLat = 51.6;
        segment.endLon = -0.2;
        segment.distance = 750.0;
        segment.createdBy = actor;
        segment.persist();
        Long segmentId = segment.id;
        tx.commit();

        given()
            .contentType("application/json")
        .when()
            .get("/api/segments/" + segmentId)
        .then()
            .statusCode(200)
            .body("id", equalTo(segmentId.intValue()))
            .body("name", equalTo("Fetchable Hill"))
            .body("description", equalTo("A known segment"))
            .body("activityType", equalTo("Run"))
            .body("distance", equalTo(750f));
    }

    @Test
    void shouldReturn404ForNonexistentSegment() {
        given()
            .contentType("application/json")
        .when()
            .get("/api/segments/99999")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldGetLeaderboard() throws Exception {
        tx.begin();
        Actor alice = new Actor("segres-dave", "Dave");
        alice.persist();
        Actor bob = new Actor("segres-eve", "Eve");
        bob.persist();

        Segment segment = new Segment();
        segment.name = "Leaderboard Segment";
        segment.description = "For leaderboard test";
        segment.activityType = "Run";
        segment.startLat = 35.0;
        segment.startLon = 139.0;
        segment.endLat = 35.1;
        segment.endLon = 139.1;
        segment.distance = 1000.0;
        segment.createdBy = alice;
        segment.persist();

        Activity activityA = new Activity();
        activityA.actor = alice;
        activityA.activityType = "Run";
        activityA.activityId = "segres-dave-activity-1";
        activityA.objectType = "Run";
        activityA.publishedAt = LocalDateTime.now();
        activityA.persist();

        Activity activityB = new Activity();
        activityB.actor = bob;
        activityB.activityType = "Run";
        activityB.activityId = "segres-eve-activity-1";
        activityB.objectType = "Run";
        activityB.publishedAt = LocalDateTime.now();
        activityB.persist();

        SegmentEffort effort1 = new SegmentEffort();
        effort1.segment = segment;
        effort1.activity = activityA;
        effort1.actor = alice;
        effort1.elapsedTime = 180L;
        effort1.startedAt = LocalDateTime.now();
        effort1.persist();

        SegmentEffort effort2 = new SegmentEffort();
        effort2.segment = segment;
        effort2.activity = activityB;
        effort2.actor = bob;
        effort2.elapsedTime = 210L;
        effort2.startedAt = LocalDateTime.now();
        effort2.persist();
        tx.commit();

        given()
            .contentType("application/json")
        .when()
            .get("/api/segments/" + segment.id + "/leaderboard")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].username", equalTo("segres-dave"))
            .body("[0].elapsedTime", equalTo(180))
            .body("[0].rank", equalTo(1))
            .body("[1].username", equalTo("segres-eve"))
            .body("[1].elapsedTime", equalTo(210))
            .body("[1].rank", equalTo(2));
    }
}
