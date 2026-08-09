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
import org.openpace.activity.Activity;

/**
 * Represents the time spent in each pace zone for an activity.
 * Pace zones are based on instantaneous pace (seconds per km).
 */
@Entity
@Table(name = "activity_pace_zone", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"activity_id", "zone_number"})
})
public class ActivityPaceZone extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    public Activity activity;

    @Column(name = "zone_number", nullable = false)
    public int zoneNumber;

    @Column(name = "zone_name", nullable = false, length = 20)
    public String zoneName;

    @Column(name = "time_in_seconds", nullable = false)
    public long timeInSeconds;

    @Column(name = "percentage", nullable = false)
    public double percentage;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public ActivityPaceZone() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Find all pace zones for an activity, ordered by zone number.
     */
    public static List<ActivityPaceZone> findByActivity(Long activityId) {
        return find("activity.id = ?1 ORDER BY zoneNumber", activityId).list();
    }
}
