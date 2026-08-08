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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.openpace.actor.Actor;

@QuarkusTest
class ActivityServiceTest {

    @Inject
    ActivityService activityService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    @TestTransaction
    void shouldCreateNoteActivity() throws Exception {
        Actor actor = new Actor("svc-alice", "Alice");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Hello World"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        assertNotNull(activity);
        assertEquals("Create", activity.activityType);
        assertEquals("Note", activity.objectType);
        assertEquals("Hello World", activity.objectContent);
        assertEquals(ActivityType.NOTE, activity.getActivityType());
    }

    @Test
    @TestTransaction
    void shouldCreateRunActivity() throws Exception {
        Actor actor = new Actor("svc-bob", "Bob");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Run",
                    "name": "Morning 5K",
                    "distance": 5000,
                    "duration": "PT25M30S"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        assertNotNull(activity);
        assertEquals("Create", activity.activityType);
        assertEquals("Run", activity.objectType);
        assertNotNull(activity.objectJson);
        assertEquals(ActivityType.RUN, activity.getActivityType());

        // Verify JSONB storage preserves all fields
        assertEquals("Morning 5K", activity.objectJson.get("name").asText());
        assertEquals(5000, activity.objectJson.get("distance").asInt());
        assertEquals("PT25M30S", activity.objectJson.get("duration").asText());
    }

    @Test
    @TestTransaction
    void shouldCreateRideActivity() throws Exception {
        Actor actor = new Actor("svc-charlie", "Charlie");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Ride",
                    "name": "Evening Ride",
                    "distance": 25000,
                    "duration": "PT1H30M"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        assertNotNull(activity);
        assertEquals("Ride", activity.objectType);
        assertNotNull(activity.objectJson);
        assertEquals(ActivityType.RIDE, activity.getActivityType());
    }

    @Test
    @TestTransaction
    void shouldRetrieveActivityWithJsonObject() throws Exception {
        Actor actor = new Actor("svc-dave", "Dave");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Swim",
                    "name": "Lap Swim",
                    "distance": 1500,
                    "duration": "PT30M"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        // Retrieve and verify object can be deserialized
        JsonNode retrievedObject = activityService.getActivityObject(activity);
        assertNotNull(retrievedObject);
        assertEquals("Swim", retrievedObject.get("type").asText());
        assertEquals("Lap Swim", retrievedObject.get("name").asText());
    }

    @Test
    @TestTransaction
    void shouldRetrieveNoteAsObject() throws Exception {
        Actor actor = new Actor("svc-eve", "Eve");
        actor.persist();

        String activityJson = """
            {
                "type": "Create",
                "object": {
                    "type": "Note",
                    "content": "Simple note"
                }
            }
            """;

        JsonNode json = objectMapper.readTree(activityJson);
        Activity activity = activityService.createActivity(actor, json);

        JsonNode retrievedObject = activityService.getActivityObject(activity);
        assertNotNull(retrievedObject);
        assertEquals("Note", retrievedObject.get("type").asText());
        assertEquals("Simple note", retrievedObject.get("content").asText());
    }
}
