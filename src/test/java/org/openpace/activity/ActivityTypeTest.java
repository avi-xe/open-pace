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
import org.junit.jupiter.api.Test;

class ActivityTypeTest {

    @Test
    void shouldHaveAllExpectedTypes() {
        ActivityType[] types = ActivityType.values();
        assertEquals(6, types.length);
        assertNotNull(ActivityType.NOTE);
        assertNotNull(ActivityType.RUN);
        assertNotNull(ActivityType.RIDE);
        assertNotNull(ActivityType.SWIM);
        assertNotNull(ActivityType.WALK);
        assertNotNull(ActivityType.HIKE);
    }

    @Test
    void shouldReturnCorrectDisplayName() {
        assertEquals("Note", ActivityType.NOTE.getDisplayName());
        assertEquals("Run", ActivityType.RUN.getDisplayName());
        assertEquals("Ride", ActivityType.RIDE.getDisplayName());
        assertEquals("Swim", ActivityType.SWIM.getDisplayName());
        assertEquals("Walk", ActivityType.WALK.getDisplayName());
        assertEquals("Hike", ActivityType.HIKE.getDisplayName());
    }

    @Test
    void shouldReturnCorrectActivityPubType() {
        assertEquals("Note", ActivityType.NOTE.getActivityPubType());
        assertTrue(ActivityType.RUN.getActivityPubType().contains("Run"));
        assertTrue(ActivityType.RIDE.getActivityPubType().contains("Ride"));
        assertTrue(ActivityType.SWIM.getActivityPubType().contains("Swim"));
        assertTrue(ActivityType.WALK.getActivityPubType().contains("Walk"));
        assertTrue(ActivityType.HIKE.getActivityPubType().contains("Hike"));
    }

    @Test
    void shouldConvertFromActivityPubType() {
        assertEquals(ActivityType.NOTE, ActivityType.fromActivityPubType("Note"));
        assertEquals(ActivityType.RUN, ActivityType.fromActivityPubType("Run"));
        assertEquals(ActivityType.RUN, ActivityType.fromActivityPubType("https://fedisports.example/ns#Run"));
    }

    @Test
    void shouldReturnNullForUnknownType() {
        assertEquals(ActivityType.NOTE, ActivityType.fromActivityPubType("Unknown"));
    }

    @Test
    void shouldConvertFromDisplayName() {
        assertEquals(ActivityType.NOTE, ActivityType.fromDisplayName("Note"));
        assertEquals(ActivityType.RUN, ActivityType.fromDisplayName("Run"));
    }
}
