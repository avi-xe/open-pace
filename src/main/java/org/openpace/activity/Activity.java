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

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "activities")
public class Activity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    @NotNull(message = "Actor is required")
    public Actor actor;

    @NotBlank(message = "Activity type is required")
    @Column(name = "activity_type", nullable = false, length = 50)
    public String activityType;

    @NotBlank(message = "Activity ID is required")
    @Column(name = "activity_id", nullable = false, unique = true, length = 500)
    public String activityId;

    @Column(name = "object_type", length = 100)
    public String objectType;

    @Column(name = "object_content", columnDefinition = "TEXT")
    public String objectContent;

    @Column(name = "object_id", length = 500)
    public String objectId;

    @NotNull(message = "Published date is required")
    @Column(name = "published_at", nullable = false)
    public LocalDateTime publishedAt;

    @Column(name = "object_json")
    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode objectJson;

    /**
     * Raw GPX XML data, stored for re-export and re-parsing.
     */
    @Column(name = "gpx_data", columnDefinition = "TEXT")
    public String gpxData;

    /**
     * Parsed track data as JSONB.
     * Structure: { "points": [{lat, lon, ele, time, speed}], "summary": {...} }
     */
    @Column(name = "track_data")
    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode trackData;

    /**
     * Visibility level: public, unlisted, or private.
     * - public: visible everywhere, federated to followers
     * - unlisted: visible on profile, not in public timelines, not federated
     * - private: only visible to the owner
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    public Visibility visibility = Visibility.PUBLIC;

    /**
     * Simplified GPX track stored as PostGIS LineString.
     * SRID 4326 = WGS84 (lat/lon coordinate system).
     * Populated when activity has GPX data.
     */
    @Column(name = "track_line", columnDefinition = "geometry(LineString, 4326)")
    public LineString trackLine;

    /**
     * Start point of the activity route.
     * Stored as PostGIS Point for fast spatial queries.
     */
    @Column(name = "start_point", columnDefinition = "geometry(Point, 4326)")
    public Point startPoint;

    /**
     * End point of the activity route.
     * Stored as PostGIS Point for fast spatial queries.
     */
    @Column(name = "end_point", columnDefinition = "geometry(Point, 4326)")
    public Point endPoint;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public Activity() {
        this.createdAt = LocalDateTime.now();
    }

    public static Activity findByActivityId(String activityId) {
        return find("activityId", activityId).firstResult();
    }

    /**
     * Get the ActivityType enum for this activity.
     */
    public ActivityType getActivityType() {
        if (objectType != null) {
            return ActivityType.fromDisplayName(objectType);
        }
        return ActivityType.NOTE;
    }
}
