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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openpace.actor.Actor;
import org.openpace.activity.Visibility;

@QuarkusTest
class PrivacyTest {

    @Inject
    ActivityService activityService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UserTransaction tx;

    @Test
    @TestTransaction
    void shouldDefaultToPublicVisibility() throws Exception {
        Actor actor = new Actor("priv-alice", "Alice");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Public note"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        assertEquals(Visibility.PUBLIC, activity.visibility);
    }

    @Test
    @TestTransaction
    void shouldSetUnlistedVisibility() throws Exception {
        Actor actor = new Actor("priv-bob", "Bob");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Unlisted note"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        activity.visibility = Visibility.UNLISTED;
        activity.persistAndFlush();

        Activity retrieved = Activity.findByActivityId(activity.activityId);
        assertEquals(Visibility.UNLISTED, retrieved.visibility);
    }

    @Test
    @TestTransaction
    void shouldSetPrivateVisibility() throws Exception {
        Actor actor = new Actor("priv-charlie", "Charlie");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Private note"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        activity.visibility = Visibility.PRIVATE;
        activity.persistAndFlush();

        Activity retrieved = Activity.findByActivityId(activity.activityId);
        assertEquals(Visibility.PRIVATE, retrieved.visibility);
    }

    @Test
    void shouldFilterPrivateFromOutbox() throws Exception {
        tx.begin();
        Actor actor = new Actor("priv-dave", "Dave");
        actor.persist();

        // Create a public activity
        String publicJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Public post"
                }
            }
            """;
        Activity publicActivity = activityService.createActivity(actor, objectMapper.readTree(publicJson));

        // Create a private activity
        String privateJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Private post"
                }
            }
            """;
        Activity privateActivity = activityService.createActivity(actor, objectMapper.readTree(privateJson));
        privateActivity.visibility = Visibility.PRIVATE;
        privateActivity.persist();
        tx.commit();

        given()
            .header("Accept", "application/activity+json")
        .when()
            .get("/users/priv-dave/outbox/page")
        .then()
            .statusCode(200)
            .body("type", org.hamcrest.Matchers.equalTo("OrderedCollectionPage"))
            .body("orderedItems", org.hamcrest.Matchers.hasSize(1))
            .body("orderedItems[0]", org.hamcrest.Matchers.equalTo(publicActivity.activityId));
    }
}
