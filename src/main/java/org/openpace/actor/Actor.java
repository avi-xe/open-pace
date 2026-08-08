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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "actors", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"username"})
})
public class Actor extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-z0-9_-]+$", message = "Username can only contain lowercase letters, numbers, underscores, and hyphens")
    @Column(nullable = false, unique = true, length = 100)
    public String username;

    @Column(length = 255)
    public String name;

    @Column(name = "user_id")
    public Long userId;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public Actor() {
        this.createdAt = LocalDateTime.now();
    }

    public Actor(String username, String name) {
        this();
        this.username = username;
        this.name = name;
    }

    public static Actor findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public String getActorId(String baseUrl) {
        return baseUrl + "/users/" + username;
    }

    public String getInboxUrl(String baseUrl) {
        return getActorId(baseUrl) + "/inbox";
    }

    public String getOutboxUrl(String baseUrl) {
        return getActorId(baseUrl) + "/outbox";
    }

    public String getFollowersUrl(String baseUrl) {
        return getActorId(baseUrl) + "/followers";
    }

    public String getFollowingUrl(String baseUrl) {
        return getActorId(baseUrl) + "/following";
    }
}
