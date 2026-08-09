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
package org.openpace.social;

import org.openpace.actor.Actor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/**
 * Repository for follower persistence and queries.
 */
@ApplicationScoped
public class FollowerRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Find all followers for an actor.
     */
    public List<Follower> findByActor(Actor actor) {
        return Follower.find("actor", actor).list();
    }

    /**
     * Find a follower by actor and follower URL.
     */
    public Optional<Follower> findByActorAndFollowerUrl(Actor actor, String followerUrl) {
        return Follower.find("actor = ?1 AND followerActorUrl = ?2", actor, followerUrl).firstResultOptional();
    }

    /**
     * Count followers for an actor.
     */
    public long countByActor(Actor actor) {
        return Follower.count("actor", actor);
    }

    /**
     * Persist a new follower relationship.
     */
    public void persist(Follower follower) {
        follower.persist();
    }

    /**
     * Delete a follower relationship.
     */
    public void delete(Follower follower) {
        follower.delete();
    }
}
