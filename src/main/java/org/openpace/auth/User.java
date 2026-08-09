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

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Local user entity. Authentication is handled externally via OIDC.
 * This entity stores the local account info linked to one or more
 * ExternalIdentity records.
 */
@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @Column(unique = true, nullable = false)
    public String username;

    @Column
    public String email;

    @Column(name = "display_name")
    public String displayName;

    @Column(nullable = false)
    public Boolean verified = true;

    @Column(nullable = false, length = 50)
    public String role = "user";

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Find a user by username.
     */
    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    /**
     * Find a user by email.
     */
    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

    /**
     * Create a new user from OIDC claims. Does not persist — caller must persist.
     */
    public static User createFromOidc(String username, String email, String displayName) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.displayName = displayName;
        user.verified = true;
        user.role = "user";
        return user;
    }
}
