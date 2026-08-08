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
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "followers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"actor_id", "follower_actor_url"})
})
public class Follower extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    public Actor actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_actor_id")
    public Actor followerActor;

    @NotBlank(message = "Follower actor URL is required")
    @Column(name = "follower_actor_url", nullable = false, length = 500)
    public String followerActorUrl;

    @NotBlank(message = "Follower inbox URL is required")
    @Column(name = "follower_inbox", nullable = false, length = 500)
    public String followerInbox;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public Follower() {
        this.createdAt = LocalDateTime.now();
    }

    public static List<Follower> findByActor(Actor actor) {
        return find("actor", actor).list();
    }

    public static Follower findByActorAndFollowerUrl(Actor actor, String followerUrl) {
        return find("actor = ?1 and followerActorUrl = ?2", actor, followerUrl).firstResult();
    }

    public static long countByActor(Actor actor) {
        return count("actor", actor);
    }
}
