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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.openpace.actor.Actor;

@QuarkusTest
class MapResourceTest {

    @Inject
    ActivityService activityService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UserTransaction tx;

    private static final String SAMPLE_GPX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="OpenPace" xmlns="http://www.topografix.com/GPX/1/1">
          <trk>
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
            </trkseg>
          </trk>
        </gpx>
        """;

    private Long createActivityWithGpx(String prefix) throws Exception {
        tx.begin();
        Actor actor = new Actor(prefix + "-user", prefix + " User");
        actor.persist();

        ObjectNode activityJson = objectMapper.createObjectNode();
        activityJson.put("type", "Create");
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "Run");
        objectNode.put("name", prefix + " Run");
        objectNode.put("gpxData", SAMPLE_GPX);
        activityJson.set("object", objectNode);

        Activity activity = activityService.createActivity(actor, activityJson);
        Long activityId = activity.id;
        tx.commit();
        return activityId;
    }

    @Test
    void shouldReturnElevationProfile() throws Exception {
        Long activityId = createActivityWithGpx("map-elev");

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/elevation")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("size()", greaterThan(0))
            .body("[0].distance", notNullValue())
            .body("[0].elevation", notNullValue());
    }

    @Test
    void shouldExportGpx() throws Exception {
        Long activityId = createActivityWithGpx("map-export");

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/export.gpx")
        .then()
            .statusCode(200)
            .header("Content-Type", "application/gpx+xml")
            .header("Content-Disposition", org.hamcrest.Matchers.containsString("activity-" + activityId + ".gpx"));
    }

    @Test
    void shouldReturn404ForMissingActivity() {
        given()
            .pathParam("activityId", 99999L)
        .when()
            .get("/api/activities/{activityId}/map.png")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn404ForActivityWithoutTrackData() throws Exception {
        tx.begin();
        Actor actor = new Actor("map-no-track", "No Track User");
        actor.persist();

        ObjectNode activityJson = objectMapper.createObjectNode();
        activityJson.put("type", "Create");
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "Note");
        objectNode.put("content", "Just a note, no GPS");
        activityJson.set("object", objectNode);

        Activity activity = activityService.createActivity(actor, activityJson);
        Long activityId = activity.id;
        tx.commit();

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/map.png")
        .then()
            .statusCode(404);
    }
}
