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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpace.actor.Actor;

@QuarkusTest
class ActivityRepositoryTest {

    @Inject
    ActivityRepository activityRepository;

    @Test
    @TestTransaction
    void shouldPersistAndFindActivity() {
        Actor actor = new Actor("repo-alice", "Alice");
        actor.persist();

        Activity activity = new Activity();
        activity.actor = actor;
        activity.activityType = "Create";
        activity.activityId = "repo-activity-1";
        activity.objectType = "Note";
        activity.objectContent = "Test content";
        activity.publishedAt = LocalDateTime.now();
        activity.persist();

        Activity found = activityRepository.findByActivityId("repo-activity-1");
        assertNotNull(found);
        assertEquals("Create", found.activityType);
    }

    @Test
    @TestTransaction
    void shouldFindActivitiesByActor() {
        Actor actor = new Actor("repo-bob", "Bob");
        actor.persist();

        Activity a1 = new Activity();
        a1.actor = actor;
        a1.activityType = "Create";
        a1.activityId = "repo-bob-activity-1";
        a1.objectType = "Note";
        a1.objectContent = "Post 1";
        a1.publishedAt = LocalDateTime.now();
        a1.persist();

        Activity a2 = new Activity();
        a2.actor = actor;
        a2.activityType = "Create";
        a2.activityId = "repo-bob-activity-2";
        a2.objectType = "Note";
        a2.objectContent = "Post 2";
        a2.publishedAt = LocalDateTime.now();
        a2.persist();

        List<Activity> activities = activityRepository.findByActor(actor);
        assertEquals(2, activities.size());
    }

    @Test
    @TestTransaction
    void shouldFindActivitiesByType() {
        Actor actor = new Actor("repo-charlie", "Charlie");
        actor.persist();

        Activity a1 = new Activity();
        a1.actor = actor;
        a1.activityType = "Create";
        a1.activityId = "repo-charlie-create-1";
        a1.objectType = "Note";
        a1.objectContent = "Create post";
        a1.publishedAt = LocalDateTime.now();
        a1.persist();

        Activity a2 = new Activity();
        a2.actor = actor;
        a2.activityType = "Announce";
        a2.activityId = "repo-charlie-announce-1";
        a2.objectId = "http://example.com/some/activity";
        a2.publishedAt = LocalDateTime.now();
        a2.persist();

        List<Activity> creates = activityRepository.findByActivityType("Create");
        assertTrue(creates.stream().allMatch(a -> "Create".equals(a.activityType)));
    }

    @Test
    @TestTransaction
    void shouldReturnNullForNonexistentActivityId() {
        Activity found = activityRepository.findByActivityId("nonexistent-id");
        assertNull(found);
    }

    @Test
    @TestTransaction
    void shouldCountActivitiesByActor() {
        Actor actor = new Actor("repo-dave", "Dave");
        actor.persist();

        Activity a1 = new Activity();
        a1.actor = actor;
        a1.activityType = "Create";
        a1.activityId = "repo-dave-activity-1";
        a1.objectType = "Note";
        a1.objectContent = "Post";
        a1.publishedAt = LocalDateTime.now();
        a1.persist();

        long count = activityRepository.countByActor(actor);
        assertEquals(1, count);
    }
}
