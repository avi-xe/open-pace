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
package org.openpace.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;

/**
 * Repository for user persistence and queries.
 */
@ApplicationScoped
public class UserRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Find a user by username.
     */
    public Optional<User> findByUsername(String username) {
        return User.find("username", username).firstResultOptional();
    }

    /**
     * Find a user by ID.
     */
    public Optional<User> findById(Long id) {
        return User.findByIdOptional(id);
    }

    /**
     * Check if a username exists.
     */
    public boolean existsByUsername(String username) {
        return User.count("username", username) > 0;
    }

    /**
     * Persist a new user.
     */
    public void persist(User user) {
        user.persist();
    }
}
