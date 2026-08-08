/*
 * Copyright 2025 The Open Pace Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openpace.segment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import org.openpace.actor.Actor;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * A named stretch of road or trail with geographic and physical characteristics.
 */
@Entity
@Table(name = "segments")
public class Segment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    public String name;

    @Column(name = "description", columnDefinition = "TEXT")
    public String description;

    @Column(name = "activity_type", nullable = false, length = 50)
    public String activityType;

    @Column(name = "start_lat", nullable = false)
    public Double startLat;

    @Column(name = "start_lon", nullable = false)
    public Double startLon;

    @Column(name = "end_lat", nullable = false)
    public Double endLat;

    @Column(name = "end_lon", nullable = false)
    public Double endLon;

    @Column(name = "distance", nullable = false)
    public Double distance;

    @Column(name = "elevation_gain")
    public Double elevationGain = 0.0;

    @Column(name = "elevation_loss")
    public Double elevationLoss = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    public Actor createdBy;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    protected Segment() {
        this.createdAt = LocalDateTime.now();
    }
}
