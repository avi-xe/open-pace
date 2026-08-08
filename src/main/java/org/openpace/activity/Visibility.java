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

/**
 * Activity visibility levels.
 *
 * Controls who can see an activity:
 * - PUBLIC: Visible to everyone, federated to all followers
 * - FOLLOWERS: Visible only to followers, federated to direct followers
 * - PRIVATE: Visible only to the author, not federated
 */
public enum Visibility {
    PUBLIC("public"),
    UNLISTED("unlisted"),
    FOLLOWERS("followers"),
    PRIVATE("private");

    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert from string value to enum.
     *
     * @param value the string value (case-insensitive)
     * @return the corresponding Visibility, or PRIVATE if unknown
     */
    public static Visibility fromString(String value) {
        if (value == null) {
            return PRIVATE;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PRIVATE;
        }
    }

    /**
     * Check if this visibility should be federated.
     */
    public boolean shouldFederate() {
        return this == PUBLIC || this == FOLLOWERS;
    }
}
