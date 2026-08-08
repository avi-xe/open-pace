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
package org.openpace.segment;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.openpace.actor.Actor;
import org.openpace.activity.Activity;

/**
 * Repository for segment persistence and queries.
 *
 * Encapsulates all database access for segments and segment efforts,
 * keeping the service layer focused on business logic.
 */
@ApplicationScoped
public class SegmentRepository {

    /**
     * Find a segment by its database ID.
     */
    public Segment findById(Long id) {
        return Segment.findById(id);
    }

    /**
     * Find all segments.
     */
    public List<Segment> findAll() {
        return Segment.findAll().list();
    }

    /**
     * Find all segments by activity type.
     */
    public List<Segment> findByActivityType(String type) {
        return Segment.find("activityType", type).list();
    }

    /**
     * Find all segment efforts for a given segment, ordered by elapsed time ascending.
     */
    public List<SegmentEffort> findBySegment(Segment s) {
        return SegmentEffort.find("segment = ?1 ORDER BY elapsedTime ASC", s).list();
    }

    /**
     * Find all segment efforts by an actor.
     */
    public List<SegmentEffort> findByActor(Actor a) {
        return SegmentEffort.find("actor", a).list();
    }

    /**
     * Find a specific segment effort by segment and activity.
     */
    public SegmentEffort findBySegmentAndActivity(Segment s, Activity a) {
        return SegmentEffort.find("segment = ?1 AND activity = ?2", s, a).firstResult();
    }

    /**
     * Count segment efforts for a given segment.
     */
    public long countBySegment(Segment s) {
        return SegmentEffort.count("segment", s);
    }

    /**
     * Persist a new segment.
     */
    public void persist(Segment s) {
        s.persist();
    }

    /**
     * Persist a new segment effort.
     */
    public void persist(SegmentEffort e) {
        e.persist();
    }
}
