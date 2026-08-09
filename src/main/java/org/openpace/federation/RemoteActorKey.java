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
package org.openpace.federation;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Caches public keys from remote ActivityPub actors.
 * Used to verify HTTP Signatures on inbound federation requests.
 */
@Entity
@Table(name = "remote_actor_key")
public class RemoteActorKey extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * The full URL of the remote actor (e.g., https://mastodon.social/users/user).
     */
    @Column(name = "actor_url", nullable = false, unique = true, columnDefinition = "TEXT")
    public String actorUrl;

    /**
     * The actor's public key in PEM format.
     */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    public String publicKey;

    /**
     * When this key was fetched from the remote server.
     */
    @Column(name = "fetched_at", nullable = false)
    public LocalDateTime fetchedAt;

    /**
     * Find cached key for a remote actor.
     *
     * @param actorUrl the remote actor's URL
     * @return the cached key, or null if not found
     */
    public static RemoteActorKey findByActorUrl(String actorUrl) {
        return find("actorUrl", actorUrl).firstResult();
    }

    /**
     * Check if the cached key is still valid (less than 24 hours old).
     *
     * @return true if the key is fresh
     */
    public boolean isFresh() {
        return fetchedAt != null && fetchedAt.isAfter(LocalDateTime.now().minusHours(24));
    }
}
