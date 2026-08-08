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

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.openpace.actor.Actor;

/**
 * Repository for activity persistence and queries.
 *
 * Encapsulates all database access for activities, keeping the service layer
 * focused on business logic.
 */
@ApplicationScoped
public class ActivityRepository {

    /**
     * Find an activity by its ActivityPub ID (URL).
     */
    public Activity findByActivityId(String activityId) {
        return Activity.findByActivityId(activityId);
    }

    /**
     * Find all activities by an actor, ordered by published date descending.
     */
    public List<Activity> findByActor(Actor actor) {
        return Activity.find("actor = ?1 ORDER BY publishedAt DESC", actor).list();
    }

    /**
     * Find all activities by activity type (e.g., "Create", "Announce").
     */
    public List<Activity> findByActivityType(String activityType) {
        return Activity.find("activityType = ?1 ORDER BY publishedAt DESC", activityType).list();
    }

    /**
     * Find all activities with a specific object type (e.g., "Note", "Run").
     */
    public List<Activity> findByObjectType(String objectType) {
        return Activity.find("objectType = ?1 ORDER BY publishedAt DESC", objectType).list();
    }

    /**
     * Count activities by actor.
     */
    public long countByActor(Actor actor) {
        return Activity.count("actor", actor);
    }

    /**
     * Persist a new activity.
     */
    public void persist(Activity activity) {
        activity.persist();
    }

    /**
     * Find activities within a given radius of a point (in meters).
     * Uses PostGIS ST_DWithin for efficient spatial queries.
     */
    public List<Activity> findNearby(double latitude, double longitude, double radiusMeters) {
        return Activity.find(
            "SELECT a FROM Activity a WHERE a.startPoint IS NOT NULL " +
            "AND func('ST_DWithin', a.startPoint, func('ST_SetSRID', func('ST_MakePoint', ?1, ?2), 4326), ?3) " +
            "ORDER BY a.publishedAt DESC",
            longitude, latitude, radiusMeters
        ).list();
    }

    /**
     * Find activities within a bounding box.
     * Uses PostGIS ST_Envelope for spatial containment.
     */
    public List<Activity> findInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        return Activity.find(
            "SELECT a FROM Activity a WHERE a.startPoint IS NOT NULL " +
            "AND func('ST_Within', a.startPoint, func('ST_SetSRID', func('ST_MakeEnvelope', ?1, ?2, ?3, ?4), 4326)) " +
            "ORDER BY a.publishedAt DESC",
            minLon, minLat, maxLon, maxLat
        ).list();
    }
}
