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
package org.openpace.core;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ActivityResourceTest {

    @Inject
    UserTransaction tx;

    @Test
    void shouldReturnActivity() throws Exception {
        tx.begin();
        Actor actor = new Actor("actres-alice", "Alice");
        actor.persist();

        // Use a simple string ID (not a URL) for easier path matching
        String activityId = "actres-alice-activity-1";

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = "Create";
        activity.activityId = activityId;
        activity.objectType = "Note";
        activity.objectContent = "Hello World";
        activity.publishedAt = LocalDateTime.now();
        activity.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/activities/" + activityId)
        .then()
            .statusCode(200)
            .contentType("application/activity+json")
            .body("type", equalTo("Create"))
            .body("id", equalTo(activityId))
            .body("actor", notNullValue());
    }

    @Test
    void shouldReturn404ForNonexistentActivity() {
        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/activities/nonexistent-activity-id")
        .then()
            .statusCode(404);
    }
}
