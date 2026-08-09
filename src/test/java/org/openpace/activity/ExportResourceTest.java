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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.openpace.actor.Actor;
import org.openpace.activity.Visibility;

@QuarkusTest
class ExportResourceTest {

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
              <trkpt lat="42.3601" lon="-71.0589">
                <ele>10</ele>
                <time>2024-01-01T10:00:00Z</time>
              </trkpt>
              <trkpt lat="42.3610" lon="-71.0580">
                <ele>15</ele>
                <time>2024-01-01T10:05:00Z</time>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
        """;

    /**
     * Create an activity with GPX data (which populates trackData).
     * Returns the string activityId for use in ExportResource endpoints.
     */
    private String createActivityWithGpx(String prefix) throws Exception {
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
        String activityId = activity.activityId;
        tx.commit();
        return activityId;
    }

    /**
     * Create a Note activity (no trackData).
     * Returns the string activityId for use in ExportResource endpoints.
     */
    private String createActivityWithoutTrackData(String prefix) throws Exception {
        tx.begin();
        Actor actor = new Actor(prefix + "-user", prefix + " User");
        actor.persist();

        ObjectNode activityJson = objectMapper.createObjectNode();
        activityJson.put("type", "Create");
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "Note");
        objectNode.put("content", "Just a note, no GPS data");
        activityJson.set("object", objectNode);

        Activity activity = activityService.createActivity(actor, activityJson);
        String activityId = activity.activityId;
        tx.commit();
        return activityId;
    }

    /**
     * Create a private activity with GPX data.
     * Returns the string activityId for use in ExportResource endpoints.
     */
    private String createPrivateActivityWithGpx(String prefix) throws Exception {
        tx.begin();
        Actor actor = new Actor(prefix + "-user", prefix + " User");
        actor.persist();

        ObjectNode activityJson = objectMapper.createObjectNode();
        activityJson.put("type", "Create");
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("type", "Run");
        objectNode.put("name", prefix + " Private Run");
        objectNode.put("gpxData", SAMPLE_GPX);
        activityJson.set("object", objectNode);

        Activity activity = activityService.createActivity(actor, activityJson);
        activity.visibility = Visibility.PRIVATE;
        activity.persist();
        String activityId = activity.activityId;
        tx.commit();
        return activityId;
    }

    @Test
    void shouldExportGpx() throws Exception {
        String activityId = createActivityWithGpx("export-gpx");

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/export/gpx")
        .then()
            .statusCode(200)
            .contentType("application/gpx+xml")
            .header("Content-Disposition", containsString(".gpx"))
            .body(containsString("<gpx"));
    }

    @Test
    void shouldExportJson() throws Exception {
        String activityId = createActivityWithGpx("export-json");

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/export/json")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .header("Content-Disposition", containsString(".json"))
            .body("type", notNullValue());
    }

    @Test
    void shouldReturn404ForNonexistentActivity() {
        given()
            .pathParam("activityId", "nonexistent-activity-id-99999")
        .when()
            .get("/api/activities/{activityId}/export/gpx")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn403ForPrivateActivity() throws Exception {
        String activityId = createPrivateActivityWithGpx("export-private");

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/export/gpx")
        .then()
            .statusCode(403);
    }

    @Test
    void shouldReturn404WhenNoTrackData() throws Exception {
        String activityId = createActivityWithoutTrackData("export-notrack");

        given()
            .pathParam("activityId", activityId)
        .when()
            .get("/api/activities/{activityId}/export/gpx")
        .then()
            .statusCode(404);
    }
}
