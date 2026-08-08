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

import org.openpace.actor.Actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ActivityTest {

    @Test
    @TestTransaction
    void shouldCreateActivity() {
        Actor actor = new Actor("activity-alice", "Alice");
        actor.persist();

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = "Create";
        activity.activityId = "http://localhost:8080/users/activity-alice/activities/1";
        activity.objectType = "Note";
        activity.objectContent = "Hello World";
        activity.publishedAt = LocalDateTime.now();
        activity.persist();

        assertNotNull(activity.id);
        assertEquals("Create", activity.activityType);
        assertEquals("http://localhost:8080/users/activity-alice/activities/1", activity.activityId);
        assertEquals("Note", activity.objectType);
        assertEquals("Hello World", activity.objectContent);
    }

    @Test
    @TestTransaction
    void shouldFindActivityByActivityId() {
        Actor actor = new Actor("activity-bob", "Bob");
        actor.persist();

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = "Create";
        activity.activityId = "http://localhost:8080/users/activity-bob/activities/42";
        activity.objectType = "Note";
        activity.objectContent = "Test post";
        activity.publishedAt = LocalDateTime.now();
        activity.persist();

        Activity found = Activity.findByActivityId("http://localhost:8080/users/activity-bob/activities/42");
        assertNotNull(found);
        assertEquals("Create", found.activityType);
    }

    @Test
    @TestTransaction
    void shouldReturnNullForNonexistentActivityId() {
        Activity found = Activity.findByActivityId("http://nonexistent/activities/999");
        assertNull(found);
    }
}
