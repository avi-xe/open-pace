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
 * Compares an activity's metrics against the user's historical averages.
 */
@Entity
@Table(name = "activity_comparison", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"activity_id", "metric_name"})
})
public class ActivityComparison extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    public Activity activity;

    @Column(name = "metric_name", nullable = false, length = 50)
    public String metricName;

    @Column(name = "activity_value", nullable = false)
    public double activityValue;

    @Column(name = "user_average", nullable = false)
    public double userAverage;

    @Column(name = "percent_diff", nullable = false)
    public double percentDiff;

    @Column(name = "is_improvement", nullable = false)
    public boolean isImprovement;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public ActivityComparison() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Find all comparisons for an activity.
     */
    public static List<ActivityComparison> findByActivity(Long activityId) {
        return find("activity.id = ?1", activityId).list();
    }
}
