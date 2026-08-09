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
package org.openpace.actor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;

/**
 * Repository for actor persistence and queries.
 */
@ApplicationScoped
public class ActorRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Find an actor by username.
     */
    public Optional<Actor> findByUsername(String username) {
        return Actor.find("username", username).firstResultOptional();
    }

    /**
     * Find an actor by ID.
     */
    public Optional<Actor> findById(Long id) {
        return Actor.findByIdOptional(id);
    }

    /**
     * Find an actor by ActorPub ID (URL).
     */
    public Optional<Actor> findByActorId(String actorId) {
        return Actor.find("actorId", actorId).firstResultOptional();
    }

    /**
     * Check if a username exists.
     */
    public boolean existsByUsername(String username) {
        return Actor.count("username", username) > 0;
    }

    /**
     * Persist a new actor.
     */
    public void persist(Actor actor) {
        actor.persist();
    }
}
