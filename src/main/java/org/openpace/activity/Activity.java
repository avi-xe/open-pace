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
import java.time.LocalDateTime;

@Entity
@Table(name = "activities")
public class Activity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    public Actor actor;

    @Column(name = "activity_type", nullable = false, length = 50)
    public String activityType;

    @Column(name = "activity_id", nullable = false, unique = true, length = 500)
    public String activityId;

    @Column(name = "object_type", length = 100)
    public String objectType;

    @Column(name = "object_content", columnDefinition = "TEXT")
    public String objectContent;

    @Column(name = "object_id", length = 500)
    public String objectId;

    @Column(name = "published_at", nullable = false)
    public LocalDateTime publishedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public Activity() {
        this.createdAt = LocalDateTime.now();
    }

    public static Activity findByActivityId(String activityId) {
        return find("activityId", activityId).firstResult();
    }
}
