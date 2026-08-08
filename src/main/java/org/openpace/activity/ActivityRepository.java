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
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

    @Inject
    EntityManager entityManager;

    /**
     * Find an activity by its ActivityPub ID (URL).
     * Uses JOIN FETCH to prevent N+1 lazy loading on actor.
     */
    public Activity findByActivityId(String activityId) {
        List<Activity> results = entityManager.createQuery(
            "SELECT a FROM Activity a JOIN FETCH a.actor WHERE a.activityId = ?1",
            Activity.class)
            .setParameter(1, activityId)
            .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Find all activities by an actor, ordered by published date descending.
     * Uses JOIN FETCH to prevent N+1 lazy loading.
     */
    public List<Activity> findByActor(Actor actor) {
        return entityManager.createQuery(
            "SELECT a FROM Activity a JOIN FETCH a.actor WHERE a.actor = ?1 ORDER BY a.publishedAt DESC",
            Activity.class)
            .setParameter(1, actor)
            .getResultList();
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
     * Returns IDs only to avoid geometry hydration issues with native queries.
     */
    @SuppressWarnings("unchecked")
    public List<Activity> findNearby(double latitude, double longitude, double radiusMeters) {
        List<Long> ids = entityManager.createNativeQuery(
            "SELECT a.id FROM activities a " +
            "WHERE a.start_point IS NOT NULL " +
            "AND ST_DWithin(a.start_point::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius) " +
            "ORDER BY a.published_at DESC")
            .setParameter("lat", latitude)
            .setParameter("lon", longitude)
            .setParameter("radius", radiusMeters)
            .getResultList();

        if (ids.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
            "SELECT a FROM Activity a JOIN FETCH a.actor WHERE a.id IN ?1 ORDER BY a.publishedAt DESC",
            Activity.class)
            .setParameter(1, ids)
            .getResultList();
    }

    /**
     * Find activities within a bounding box.
     * Uses PostGIS ST_Within for spatial containment.
     * Returns IDs only to avoid geometry hydration issues with native queries.
     */
    @SuppressWarnings("unchecked")
    public List<Activity> findInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        List<Long> ids = entityManager.createNativeQuery(
            "SELECT a.id FROM activities a " +
            "WHERE a.start_point IS NOT NULL " +
            "AND ST_Within(a.start_point, ST_SetSRID(ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat), 4326)) " +
            "ORDER BY a.published_at DESC")
            .setParameter("minLat", minLat)
            .setParameter("minLon", minLon)
            .setParameter("maxLat", maxLat)
            .setParameter("maxLon", maxLon)
            .getResultList();

        if (ids.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
            "SELECT a FROM Activity a JOIN FETCH a.actor WHERE a.id IN ?1 ORDER BY a.publishedAt DESC",
            Activity.class)
            .setParameter(1, ids)
            .getResultList();
    }
}
