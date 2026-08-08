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
 * Supported activity types for Open Pace.
 *
 * Each type has a display name (for UI) and an ActivityPub type (for federation).
 * Custom sports types use the Fediverse Sports namespace.
 */
public enum ActivityType {

    NOTE("Note", "Note"),
    RUN("Run", "https://fedisports.example/ns#Run"),
    RIDE("Ride", "https://fedisports.example/ns#Ride"),
    SWIM("Swim", "https://fedisports.example/ns#Swim"),
    WALK("Walk", "https://fedisports.example/ns#Walk"),
    HIKE("Hike", "https://fedisports.example/ns#Hike");

    private final String displayName;
    private final String activityPubType;

    ActivityType(String displayName, String activityPubType) {
        this.displayName = displayName;
        this.activityPubType = activityPubType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getActivityPubType() {
        return activityPubType;
    }

    /**
     * Convert from ActivityPub type string to ActivityType enum.
     * Handles both simple types ("Run") and full URIs ("https://fedisports.example/ns#Run").
     *
     * @param activityPubType the ActivityPub type string
     * @return the matching ActivityType, or NOTE if unknown
     */
    public static ActivityType fromActivityPubType(String activityPubType) {
        if (activityPubType == null) {
            return NOTE;
        }
        for (ActivityType type : values()) {
            if (type.activityPubType.equals(activityPubType) || type.displayName.equals(activityPubType)) {
                return type;
            }
        }
        return NOTE;
    }

    /**
     * Convert from display name to ActivityType enum.
     *
     * @param displayName the display name
     * @return the matching ActivityType, or NOTE if unknown
     */
    public static ActivityType fromDisplayName(String displayName) {
        if (displayName == null) {
            return NOTE;
        }
        for (ActivityType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return NOTE;
    }
}
