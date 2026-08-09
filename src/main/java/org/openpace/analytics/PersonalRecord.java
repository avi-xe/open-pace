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
package org.openpace.analytics;

import org.openpace.activity.Activity;
import org.openpace.actor.Actor;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "personal_record", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"actor_id", "activity_type", "distance_label"})
})
public class PersonalRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    public Actor actor;

    @Column(name = "activity_type", nullable = false, length = 50)
    public String activityType;

    @Column(name = "distance_label", nullable = false, length = 20)
    public String distanceLabel;

    @Column(name = "distance_meters", nullable = false)
    public double distanceMeters;

    @Column(name = "elapsed_time", nullable = false)
    public long elapsedTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    public Activity activity;

    @Column(name = "achieved_at", nullable = false)
    public LocalDateTime achievedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public PersonalRecord() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Find personal record for a specific actor, activity type, and distance.
     */
    public static PersonalRecord findByActorAndTypeAndDistance(Long actorId, String activityType, String distanceLabel) {
        return find("actor.id = ?1 and activityType = ?2 and distanceLabel = ?3",
                actorId, activityType, distanceLabel).firstResult();
    }

    /**
     * Find all personal records for a specific actor.
     */
    public static List<PersonalRecord> findByActor(Long actorId) {
        return find("actor.id", actorId).list();
    }

    /**
     * Find personal records for a specific actor and activity type.
     */
    public static List<PersonalRecord> findByActorAndType(Long actorId, String activityType) {
        return find("actor.id = ?1 and activityType = ?2", actorId, activityType).list();
    }

    /**
     * Count personal records for a specific actor.
     */
    public static long countByActor(Long actorId) {
        return count("actor.id", actorId);
    }
}
